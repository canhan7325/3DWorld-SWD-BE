package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.entity.UserRoleId;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.repository.RoleRepository;
import com.project.w_3d_world.features.auth.repository.UserRoleRepository;
import com.project.w_3d_world.features.auth.service.impl.AccountValidation;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.enums.AccountStatus;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final AccountAuthorization authorization;
    private final com.project.w_3d_world.features.wallet.service.CommissionWalletService sellerWallet;
    private final PasswordEncoder passwords;
    private final EmailTokenService email;
    private final AuditLogRepository audit;
    private final JdbcTemplate jdbc;
    private final SellerVerificationService sellers;
    private final UserBankAccountRepository banks;

    public record Account(int id, String email, String fullName, AccountStatus status,
                          boolean emailVerified, List<String> grants) {}

    @Transactional(readOnly = true)
    public Page<Account> list(int actor, int page, int size) {
        authorization.requireRole(actor, RoleCode.ADMIN);
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidRequest("Invalid page or size.");
        }
        return users.findAll(PageRequest.of(page, size, org.springframework.data.domain.Sort.by("id")))
                .map(this::response);
    }

    @Transactional
    public Account createInternal(int actor, String emailValue, String password, String name,
                                   String phone, RoleCode role) {
        authorization.requireRole(actor, RoleCode.ADMIN);
        if (role != RoleCode.PRINT_STAFF && role != RoleCode.DESIGNER) {
            throw ApiException.invalidRequest("Only PRINT_STAFF and DESIGNER can be created by this endpoint.");
        }
        String normalizedEmail = AccountValidation.email(emailValue);
        AccountValidation.password(password);
        if (users.findByNormalizedEmail(normalizedEmail).isPresent()) {
            throw ApiException.emailAlreadyExists();
        }
        User user = users.saveAndFlush(new User(normalizedEmail, passwords.encode(password),
                AccountValidation.required(name, 255, "Full name"), AccountValidation.phone(phone)));
        grant(user, RoleCode.CUSTOMER, actor);
        grant(user, role, actor);
        email.createVerification(user);
        audit.save(new AuditLog(actor, "INTERNAL_ACCOUNT_CREATED", "USER", user.getId(),
                "Admin-created internal account; email verification required", "role=" + role));
        return response(user);
    }

    @Transactional
    public Account role(int actor, int target, RoleCode role, boolean active, String reason) {
        lockAdminPolicy();
        authorization.requireRole(actor, RoleCode.ADMIN);
        String why = AccountValidation.required(reason, 1000, "Role change reason");
        if (role == RoleCode.CUSTOMER) {
            throw ApiException.invalidRequest("The CUSTOMER base role cannot be removed or reassigned.");
        }
        User user = users.lockById(target).orElseThrow(() -> ApiException.invalidRequest("User does not exist."));
        if (active && role == RoleCode.ADMIN && (!user.isActive() || !user.isEmailVerified())) {
            throw ApiException.invalidRequest("Admin role requires an active account with verified email.");
        }
        if (active && role == RoleCode.SELLER) {
            sellerWallet.requireReserve(target);
            Integer eligible = jdbc.queryForObject("""
                    SELECT count(*) FROM storefronts WHERE owner_user_id=? AND status='ACTIVE'
                    AND seller_verification_status='VERIFIED' AND expires_at>now()
                    """, Integer.class, target);
            if (!sellers.eligible(user) || eligible == null || eligible < 1) {
                throw ApiException.invalidRequest("Seller role requires verified eligibility and a paid active storefront.");
            }
        }
        var roleEntity = roles.findByCode(role).orElseThrow();
        var existing = userRoles.findById(new UserRoleId(target, roleEntity.getId())).orElse(null);
        if (!active && role == RoleCode.ADMIN && existing != null && existing.isActive()
                && user.isActive() && activeAdminCount() <= 1) {
            throw ApiException.invalidRequest("The last active Admin cannot be removed.");
        }
        if (existing == null) {
            if (!active) {
                return response(user);
            }
            existing = grant(user, role, actor);
        } else {
            if (existing.isActive() == active) {
                return response(user);
            }
            existing.changeApproval(active, actor);
        }
        user.revokeSessions();
        audit.save(new AuditLog(actor, active ? "ROLE_GRANTED" : "ROLE_REVOKED", "USER", target,
                why, "role=" + role));
        return response(user);
    }

    @Transactional
    public Account status(int actor, int target, AccountStatus next, String reason) {
        lockAdminPolicy();
        authorization.requireRole(actor, RoleCode.ADMIN);
        String why = AccountValidation.required(reason, 1000, "Status change reason");
        User user = users.lockById(target).orElseThrow(() -> ApiException.invalidRequest("User does not exist."));
        if (next == null) {
            throw ApiException.invalidRequest("Account status is required.");
        }
        if (next == user.getAccountStatus()) {
            return response(user);
        }
        if (next != AccountStatus.ACTIVE && user.isActive() && isActiveAdmin(user) && activeAdminCount() <= 1) {
            throw ApiException.invalidRequest("The last active Admin cannot be locked or banned.");
        }
        user.changeStatus(next);
        audit.save(new AuditLog(actor, "ACCOUNT_STATUS_CHANGED", "USER", target, why, "status=" + next));
        // No order cancellation/refund/deletion is performed by account sanctions.
        return response(user);
    }

    @Transactional
    public Account reviewPhone(int actor, int target, String expectedPhone, boolean approved,
                                String evidenceReference, String reason) {
        authorization.requireRole(actor, RoleCode.ADMIN);
        if (actor == target) {
            throw ApiException.invalidRequest("You cannot verify your own phone.");
        }
        User user = users.lockById(target).filter(User::isActive).orElseThrow(ApiException::invalidCredentials);
        if (user.getPhone() == null || !user.getPhone().equals(AccountValidation.phone(expectedPhone))) {
            throw ApiException.invalidRequest("Phone changed. Refresh before reviewing.");
        }
        String evidence = AccountValidation.required(evidenceReference, 500, "Evidence reference");
        String why = AccountValidation.required(reason, 1000, "Review reason");
        user.reviewPhone(approved);
        if (!approved) {
            banks.findByUserIdAndActiveTrueOrderByIdAsc(target).forEach(value -> value.invalidateVerification());
            sellers.invalidate(user, "Phone verification rejected");
        }
        audit.save(new AuditLog(actor, "PHONE_VERIFICATION_REVIEWED", "USER", target, why,
                JsonValues.write(Map.of(
                        "mode", "MANUAL_SANDBOX", "approved", approved, "evidenceReference", evidence))));
        return response(user);
    }

    private UserRole grant(User user, RoleCode role, int actor) {
        var entity = roles.findByCode(role).orElseThrow();
        UserRole grant = new UserRole(user, entity);
        grant.changeApproval(true, actor);
        userRoles.saveAndFlush(grant);
        user.getUserRoles().add(grant);
        return grant;
    }

    private Account response(User user) {
        return new Account(user.getId(), user.getEmail(), user.getFullName(), user.getAccountStatus(),
                user.isEmailVerified(), user.getUserRoles().stream()
                    .map(value -> value.getRole().getCode() + ":" + value.getApprovalStatus()).sorted().toList());
    }

    private boolean isActiveAdmin(User user) {
        return user.getUserRoles().stream().anyMatch(value -> value.isActive()
                && value.getRole().getCode() == RoleCode.ADMIN);
    }

    private int activeAdminCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM user_roles ur JOIN roles r USING(role_id)
                JOIN users u USING(user_id) WHERE r.role_code='ADMIN' AND ur.approval_status='ACTIVE'
                AND u.account_status='ACTIVE' AND u.email_verified
                """, Integer.class);
    }

    private void lockAdminPolicy() {
        jdbc.execute("SELECT pg_advisory_xact_lock(3812201)");
    }
}
