package com.project.w_3d_world.features.storefront.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.entity.UserRoleId;
import com.project.w_3d_world.features.auth.enums.ApprovalStatus;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.repository.*;
import com.project.w_3d_world.features.auth.service.SellerVerificationService;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.storefront.dto.StorefrontDtos.*;
import com.project.w_3d_world.features.storefront.entity.*;
import com.project.w_3d_world.features.storefront.enums.StorefrontStatus;
import com.project.w_3d_world.features.storefront.enums.VerificationStatus;
import com.project.w_3d_world.features.storefront.repository.*;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorefrontService {
    private final StorefrontRepository stores;
    private final StorefrontSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final SellerVerificationService verification;
    private final RoleRepository roles;
    private final UserRoleRepository grants;
    private final AuditLogRepository audit;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    @Value("${app.storefront.monthly-fee:100000}")
    private BigDecimal configuredFee;

    public ShopResponse publicShop(int id) {
        Storefront store = stores.findById(id).filter(value -> value.getOwner() != null
                && value.getOwner().isActive() && value.getOwner().isEmailVerified()
                && value.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Storefront does not exist."));
        return ShopResponse.from(store);
    }

    public ShopResponse mine(int userId) {
        return ShopResponse.from(owned(userId));
    }

    @Transactional
    public ShopResponse create(int userId, CreateRequest request) {
        User user = users.lockById(userId).filter(User::isActive).orElseThrow(ApiException::invalidCredentials);
        requireEligible(user);
        if (stores.findByOwnerUserId(userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have a storefront.");
        }
        if (request.name().isBlank()) {
            throw ApiException.invalidRequest("Storefront name is required.");
        }
        var store = stores.saveAndFlush(new Storefront(userId, request.name().trim(), request.description()));
        audit.save(new AuditLog(userId, "STOREFRONT_CREATED", "STOREFRONT", store.getId(), "Storefront created", null));
        return ShopResponse.from(store);
    }

    @Transactional
    public ShopResponse update(int userId, UpdateRequest request) {
        users.lockById(userId).filter(User::isActive).orElseThrow(ApiException::invalidCredentials);
        var store = stores.lockById(owned(userId).getId()).orElseThrow();
        if (request.name() != null && request.name().isBlank()) {
            throw ApiException.invalidRequest("Storefront name is required.");
        }
        store.edit(request.name() == null ? null : request.name().trim(), request.description());
        audit.save(new AuditLog(userId, "STOREFRONT_UPDATED", "STOREFRONT", store.getId(), "Profile updated", null));
        return ShopResponse.from(store);
    }

    public SubscriptionOverview subscription(int userId) {
        var store = owned(userId);
        return new SubscriptionOverview(fee(), store.getExpiresAt(),
                subscriptions.findFirstByStorefrontIdOrderByIdDesc(store.getId())
                        .map(SubscriptionResponse::from).orElse(null),
                subscriptions.findFirstByStorefrontIdAndStatusOrderByIdDesc(store.getId(), "PENDING_PAYMENT")
                        .map(SubscriptionResponse::from).orElse(null));
    }

    @Transactional
    public SubscriptionResponse subscribe(int userId) {
        User user = users.lockById(userId).filter(User::isActive).orElseThrow(ApiException::invalidCredentials);
        requireEligible(user);
        var store = stores.lockById(owned(userId).getId()).orElseThrow();
        if (store.getStatus() == StorefrontStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "A suspended storefront cannot subscribe or renew.");
        }
        var subscription = subscriptions.findFirstByStorefrontIdAndStatusOrderByIdDesc(
                store.getId(), "PENDING_PAYMENT").orElseGet(() -> subscriptions.saveAndFlush(
                new StorefrontSubscription(store.getId(), fee())));
        return SubscriptionResponse.from(subscription);
    }

    /** Only called inside a verified provider callback/query transaction, never from the FE redirect. */
    @Transactional
    public void activateVerifiedPayment(PaymentTransaction transaction) {
        if (!"SUCCESS".equals(transaction.getStatus()) || !"PAYMENT".equals(transaction.getTransactionType())
                || !"SUBSCRIPTION".equals(transaction.getPurpose()) || transaction.getSubscriptionId() == null) {
            throw ApiException.invalidRequest("Verified subscription payment is required.");
        }
        var subscription = subscriptions.findById(transaction.getSubscriptionId()).orElseThrow();
        var snapshot = stores.findById(subscription.getStorefrontId()).orElseThrow();
        User owner = users.lockById(snapshot.getOwnerUserId()).orElseThrow();
        var store = stores.lockById(snapshot.getId()).orElseThrow();
        // The storefront lock serializes distinct transactions as well as duplicate callbacks.
        entityManager.refresh(subscription);
        if (subscription.getPaidAt() != null) {
            transaction.fulfillment("COMPLETED");
            return;
        }
        if (!"PENDING_PAYMENT".equals(subscription.getStatus())
                || !owner.getId().equals(transaction.getPayerUserId())
                || transaction.getAmount().compareTo(subscription.getFeeSnapshot()) != 0
                || store.getStatus() == StorefrontStatus.SUSPENDED || !verification.eligible(owner)) {
            transaction.fulfillment("RECONCILIATION_REQUIRED");
            return;
        }
        var role = roles.findByCode(RoleCode.SELLER).orElseThrow();
        var grant = grants.findById(new UserRoleId(owner.getId(), role.getId()))
                .orElseGet(() -> new UserRole(owner, role));
        if (grant.getApprovalStatus() == ApprovalStatus.REVOKED
                || grant.getApprovalStatus() == ApprovalStatus.REJECTED) {
            transaction.fulfillment("RECONCILIATION_REQUIRED");
            return;
        }
        Instant now = Instant.now();
        Instant start = store.getExpiresAt() != null && store.getExpiresAt().isAfter(now) ? store.getExpiresAt() : now;
        Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        subscription.activate(start, end);
        store.activateUntil(end);
        if (grant.getId() == null || !grants.existsById(grant.getId()) || !grant.isActive()) {
            grant.changeApproval(true, owner.getId());
            grants.save(grant);
            owner.revokeSessions();
        }
        transaction.fulfillment("COMPLETED");
        audit.save(new AuditLog(owner.getId(), "SUBSCRIPTION_ACTIVATED", "STOREFRONT", store.getId(),
                "Verified subscription payment", "Transaction " + transaction.getId()));
    }

    private Storefront owned(int userId) {
        return stores.findByOwnerUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Storefront does not exist."));
    }

    private void requireEligible(User user) {
        if (!verification.eligible(user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Approved seller verification is required.");
        }
    }

    private BigDecimal fee() {
        var value = jdbc.query("SELECT config_value FROM platform_configs WHERE config_key='STOREFRONT_MONTHLY_FEE'",
                (rs, row) -> rs.getString(1)).stream().findFirst();
        BigDecimal fee;
        try {
            fee = value.map(BigDecimal::new).orElse(configuredFee);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid storefront fee configuration.");
        }
        if (fee == null || fee.signum() <= 0 || fee.stripTrailingZeros().scale() > 0) {
            throw new IllegalStateException("Storefront monthly fee must be a positive whole VND amount.");
        }
        return fee;
    }
}
