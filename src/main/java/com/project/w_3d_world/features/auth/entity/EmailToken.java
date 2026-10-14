package com.project.w_3d_world.features.auth.entity;

import com.project.w_3d_world.features.auth.enums.DeliveryStatus;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.user.entity.User;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_email_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "encrypted_token", columnDefinition = "text")
    private String encryptedToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public EmailToken(User user, TokenPurpose purpose, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.recipient = user.getEmail();
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void setEncryptedToken(String encryptedToken) {
        this.encryptedToken = encryptedToken;
    }

    public boolean canBeUsed(String email, TokenPurpose expectedPurpose) {
        return recipient.equalsIgnoreCase(email)
                && purpose == expectedPurpose
                && usedAt == null
                && expiresAt.isAfter(Instant.now())
                && deliveryStatus == DeliveryStatus.SENT;
    }

    public void consume() {
        usedAt = Instant.now();
    }

    public void cancelDelivery() {
        deliveryStatus = DeliveryStatus.CANCELLED;
        encryptedToken = null;
    }

    public void markDelivered() {
        deliveryAttempts++;
        deliveryStatus = DeliveryStatus.SENT;
        deliveredAt = Instant.now();
        lastError = null;
        encryptedToken = null;
    }

    public void markFailed(String error) {
        deliveryAttempts++;
        deliveryStatus = DeliveryStatus.FAILED;
        lastError = error.substring(0, Math.min(error.length(), 100));
        long delaySeconds = Math.min(300, 30L << Math.min(deliveryAttempts - 1, 3));
        nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
    }
}
