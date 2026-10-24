package com.project.w_3d_world.features.user.entity;

import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.user.enums.AccountStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "avatar_public_id", length = 255)
    private String avatarPublicId;

    @Column(length = 30)
    private String phone;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "ai_credits_balance", nullable = false)
    private int aiCreditsBalance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "google_id", length = 255, unique = true)
    private String googleId;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "email_verification_version", nullable = false)
    private long emailVerificationVersion;

    @Column(name = "password_reset_version", nullable = false)
    private long passwordResetVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "email_delivery_state", nullable = false, columnDefinition = "jsonb")
    private String emailDeliveryState = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seller_verification_state", nullable = false, columnDefinition = "jsonb")
    private String sellerVerificationState = "{}";

    public User(String email, String passwordHash, String fullName, String phone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
    }

    public void verifyEmail() {
        emailVerified = true;
        updatedAt = Instant.now();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        updatedAt = Instant.now();
    }

    public void revokeSessions() {
        authVersion = Math.incrementExact(authVersion);
        updatedAt = Instant.now();
    }

    public void updateProfile(String fullName, String phone) {
        this.fullName = fullName;
        if (!Objects.equals(this.phone, phone)) {
            phoneVerified = false;
        }
        this.phone = phone;
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public void changeAvatar(String url, String publicId) {
        avatarUrl = url;
        avatarPublicId = publicId;
    }
    public void updateDateOfBirth(LocalDate value) {
        dateOfBirth = value;
    }
    public long nextEmailVersion(TokenPurpose purpose) {
        if (purpose == TokenPurpose.VERIFY_EMAIL) {
            return emailVerificationVersion = Math.incrementExact(emailVerificationVersion);
        }
        return passwordResetVersion = Math.incrementExact(passwordResetVersion);
    }

    public long emailVersion(TokenPurpose purpose) {
        return purpose == TokenPurpose.VERIFY_EMAIL
                ? emailVerificationVersion : passwordResetVersion;
    }

    public void emailDeliveryState(String value) {
        emailDeliveryState = value;
    }

    public void sellerVerificationState(String value) {
        sellerVerificationState = value;
    }

    public void reviewPhone(boolean approved) {
        phoneVerified = approved;
        updatedAt = Instant.now();
    }

    public void changeStatus(AccountStatus value) {
        accountStatus = value;
        emailVerificationVersion = Math.incrementExact(emailVerificationVersion);
        passwordResetVersion = Math.incrementExact(passwordResetVersion);
        revokeSessions();
    }
}
