package com.project.w_3d_world.features.user.entity;

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
@Table(name = "user_bank_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_account_id")
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "bank_code", nullable = false)
    private String bankCode;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_holder", nullable = false)
    private String accountHolder;

    @Column(name = "account_number_token", nullable = false)
    private String accountNumberToken;

    @Column(name = "account_number_masked", nullable = false)
    private String accountNumberMasked;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "PENDING";

    @Column(name = "is_default", nullable = false)
    private boolean defaultAccount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private String environment = "SANDBOX";

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UserBankAccount(
            Integer userId,
            String bankCode,
            String bankName,
            String accountHolder,
            String token,
            String masked) {
        this.userId = userId;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.accountNumberToken = token;
        this.accountNumberMasked = masked;
    }

    public void update(
            String bankCode,
            String bankName,
            String accountHolder,
            String token,
            String masked) {
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountHolder = accountHolder;
        this.accountNumberToken = token;
        this.accountNumberMasked = masked;
        this.verificationStatus = "PENDING";
        this.verifiedAt = null;
    }

    public void markDefault(boolean value) {
        this.defaultAccount = value;
    }

    public void deactivate() {
        this.active = false;
        this.defaultAccount = false;
    }

    public void review(boolean approved) {
        this.verificationStatus = approved ? "VERIFIED" : "REJECTED";
        this.verifiedAt = approved ? Instant.now() : null;
    }
}
