package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.impl.AccountValidation;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.entity.UserBankAccount;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SellerVerificationService {
    private final UserRepository users;
    private final UserBankAccountRepository banks;
    private final StorefrontRepository storefronts;
    private final AuditLogRepository audit;
    private final AccountAuthorization authorization;
    private final ObjectMapper mapper;

    public record State(String status, String phoneSnapshot, Integer bankAccountId,
                        String evidenceReference, long submittedAt, Long reviewedAt,
                        Integer reviewerId, String reason) {}

    public record Response(int userId, boolean emailVerified, boolean phoneVerified,
                           boolean verifiedBankAvailable, boolean eligible, State verification) {}

    public State state(User user) {
        if (user.getSellerVerificationState() == null || user.getSellerVerificationState().equals("{}")) {
            return new State("NOT_SUBMITTED", null, null, null, 0, null, null, null);
        }
        return mapper.readValue(user.getSellerVerificationState(), State.class);
    }

    @Transactional(readOnly = true)
    public Response get(int userId) {
        return response(users.findById(userId).filter(User::isActive).orElseThrow(ApiException::invalidCredentials));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Response> queue(int reviewer, String status, int page, int size) {
        authorization.requireRole(reviewer, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        if (!java.util.Set.of("NOT_SUBMITTED", "PENDING", "VERIFIED", "REJECTED").contains(status)
                || page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidRequest("Invalid verification status or pagination.");
        }
        return users.findSellerVerificationQueue(status, org.springframework.data.domain.PageRequest.of(page, size))
                .map(this::response);
    }

    @Transactional
    public Response submit(int userId, int bankId, String evidence) {
        User user = users.lockById(userId).filter(User::isActive).orElseThrow(ApiException::invalidCredentials);
        requireContact(user);
        UserBankAccount bank = bank(userId, bankId);
        String reference = AccountValidation.required(evidence, 500, "Evidence reference");
        State old = state(user);
        if (("PENDING".equals(old.status()) || "VERIFIED".equals(old.status()))
                && Objects.equals(old.phoneSnapshot(), user.getPhone())
                && Objects.equals(old.bankAccountId(), bankId)) {
            return response(user);
        }
        State next = new State("PENDING", user.getPhone(), bank.getId(), reference,
                Math.max(System.currentTimeMillis(), old.submittedAt() + 1), null, null, null);
        user.sellerVerificationState(mapper.writeValueAsString(next));
        audit.save(new AuditLog(userId, "SELLER_VERIFICATION_SUBMITTED", "USER", userId,
                "Seller verification submitted",
                mapper.writeValueAsString(Map.of("status", "PENDING", "bankId", bankId))));
        return response(user);
    }

    @Transactional
    public Response review(int reviewerId, int userId, long submittedAt, boolean approved, String reason) {
        authorization.requireRole(reviewerId, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        if (reviewerId == userId) {
            throw ApiException.invalidRequest("You cannot review your own verification.");
        }
        User user = users.lockById(userId).filter(User::isActive).orElseThrow(ApiException::invalidCredentials);
        State old = state(user);
        if (!"PENDING".equals(old.status()) || old.submittedAt() != submittedAt) {
            throw ApiException.invalidRequest("Verification changed. Refresh the pending request.");
        }
        String decisionReason = AccountValidation.required(reason, 1000, "Review reason");
        if (approved) {
            requireContact(user);
            bank(userId, old.bankAccountId());
            if (!Objects.equals(user.getPhone(), old.phoneSnapshot())) {
                throw ApiException.invalidRequest("The phone changed. Submit verification again.");
            }
        }
        State next = new State(approved ? "VERIFIED" : "REJECTED", old.phoneSnapshot(), old.bankAccountId(),
                old.evidenceReference(), old.submittedAt(), System.currentTimeMillis(), reviewerId, decisionReason);
        user.sellerVerificationState(mapper.writeValueAsString(next));
        storefronts.findByOwnerUserId(userId).ifPresent(store -> store.reviewVerification(approved));
        audit.save(new AuditLog(reviewerId, "SELLER_VERIFICATION_REVIEWED", "USER", userId,
                decisionReason, mapper.writeValueAsString(next)));
        return response(user);
    }

    @Transactional
    public void invalidate(User user, String reason) {
        State old = state(user);
        if ("VERIFIED".equals(old.status()) || "PENDING".equals(old.status())) {
            user.sellerVerificationState(mapper.writeValueAsString(new State("NOT_SUBMITTED", null,
                    null, null, old.submittedAt(), null, null, reason)));
            audit.save(new AuditLog(user.getId(), "SELLER_VERIFICATION_INVALIDATED",
                    "USER", user.getId(), reason, null));
        }
        storefronts.findByOwnerUserId(user.getId()).ifPresent(store -> store.invalidateVerification());
    }

    @Transactional(readOnly = true)
    public boolean canSubmit(User user) {
        return user.isActive() && user.isEmailVerified() && user.isPhoneVerified() && user.getPhone() != null
                && banks.findByUserIdAndActiveTrueOrderByIdAsc(user.getId()).stream()
                    .anyMatch(bank -> "VERIFIED".equals(bank.getVerificationStatus()));
    }

    @Transactional(readOnly = true)
    public boolean eligible(User user) {
        State state = state(user);
        return user.isActive() && user.isEmailVerified() && user.isPhoneVerified()
                && "VERIFIED".equals(state.status()) && Objects.equals(user.getPhone(), state.phoneSnapshot())
                && state.bankAccountId() != null && banks.findById(state.bankAccountId())
                    .filter(value -> value.getUserId().equals(user.getId()) && value.isActive()
                            && "VERIFIED".equals(value.getVerificationStatus())).isPresent();
    }

    private Response response(User user) {
        boolean bankAvailable = banks.findByUserIdAndActiveTrueOrderByIdAsc(user.getId()).stream()
                .anyMatch(value -> "VERIFIED".equals(value.getVerificationStatus()));
        return new Response(user.getId(), user.isEmailVerified(), user.isPhoneVerified(),
                bankAvailable, eligible(user), state(user));
    }

    private UserBankAccount bank(int userId, Integer bankId) {
        return banks.findById(bankId).filter(value -> value.getUserId().equals(userId)
                && value.isActive() && "VERIFIED".equals(value.getVerificationStatus()))
                .orElseThrow(() -> ApiException.invalidRequest("An owned active VERIFIED bank account is required."));
    }

    private void requireContact(User user) {
        if (!user.isEmailVerified() || !user.isPhoneVerified() || user.getPhone() == null) {
            throw ApiException.invalidRequest("Verified email and phone are required.");
        }
    }
}
