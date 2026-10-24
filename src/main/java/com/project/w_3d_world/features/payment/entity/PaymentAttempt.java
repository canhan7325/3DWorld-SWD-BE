package com.project.w_3d_world.features.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "transaction_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Integer id;

    @Column(name = "transaction_id", nullable = false)
    private Integer transactionId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "provider_name", nullable = false)
    private String providerName = "ZALOPAY";
    @Column(name = "external_reference")
    private String externalReference;
    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;
    @Column(name = "attempt_status", nullable = false)
    private String attemptStatus = "PENDING";
    @Column(name = "requested_at")
    private Instant requestedAt;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "app_id")
    private Integer appId;

    @Column(name = "app_time")
    private Long appTime;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "fingerprint", length = 255)
    private String fingerprint;

    @Column(name = "gateway_state", length = 60)
    private String gatewayState;

    @Column(name = "last_queried_at")
    private Instant lastQueriedAt;

    @Column(name = "order_url", columnDefinition = "text")
    private String orderUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_event_snapshot", columnDefinition = "jsonb")
    private String providerEventSnapshot;

    @Column(name = "reconciliation_reason", columnDefinition = "text")
    private String reconciliationReason;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_destination_snapshot", columnDefinition = "jsonb")
    private String bankDestinationSnapshot;

    public PaymentAttempt(Integer transactionId, String idempotencyKey, String paymentMethod) {
        this.transactionId = transactionId;
        this.attemptNumber = 1;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.requestedAt = Instant.now();
        this.attemptStatus = "REQUESTED";
    }

    public void initializeProvider(int number, int applicationId, long time, Instant expiry) {
        attemptNumber = number;
        appId = applicationId;
        appTime = time;
        expiresAt = expiry;
        updatedAt = Instant.now();
    }

    public void providerResult(String url, String state) {
        orderUrl = url;
        gatewayState = state;
        updatedAt = Instant.now();
    }

    public void queried() {
        lastQueriedAt = Instant.now();
    }

    public void recordEvent(String json) {
        providerEventSnapshot = json;
    }

    public void reconciliation(String reason) {
        reconciliationReason = reason;
        updatedAt = Instant.now();
    }

    public void reference(String reference) {
        externalReference = reference;
    }
    public void providerTransaction(String transactionId) {
        providerTransactionId = transactionId;
    }
    public void success() {
        attemptStatus = "SUCCESS";
        verifiedAt = Instant.now();
        completedAt = Instant.now();
    }
    public void fail() {
        attemptStatus = "FAILED";
        completedAt = Instant.now();
    }
}
