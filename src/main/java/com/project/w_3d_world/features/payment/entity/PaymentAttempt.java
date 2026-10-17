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

    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "provider_name", nullable = false) private String providerName = "ZALOPAY";
    @Column(name = "external_reference") private String externalReference;
    @Column(name = "attempt_status", nullable = false) private String attemptStatus = "PENDING";
    @Column(name = "requested_at") private Instant requestedAt;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;

    public PaymentAttempt(Integer transactionId, String idempotencyKey, String paymentMethod) {
        this.transactionId = transactionId;
        this.attemptNumber = 1;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.requestedAt = Instant.now();
        this.attemptStatus = "REQUESTED";
    }

    public void reference(String reference) { externalReference = reference; }
    public void success() { attemptStatus = "SUCCESS"; verifiedAt = Instant.now(); completedAt = Instant.now(); }
    public void fail() { attemptStatus = "FAILED"; completedAt = Instant.now(); }
}
