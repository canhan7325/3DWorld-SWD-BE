package com.project.w_3d_world.features.storefront.entity;

import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.storefront.enums.StorefrontStatus;
import com.project.w_3d_world.features.storefront.enums.VerificationStatus;
import com.project.w_3d_world.features.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "storefronts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Storefront {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "storefront_id")
    private Integer id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Integer ownerUserId;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "owner_user_id", insertable = false, updatable = false)
    private User owner;

    @Column(name = "storefront_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StorefrontStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public boolean canPublish() {
        return owner != null && owner.isActive() && owner.isEmailVerified() && owner.isPhoneVerified()
                && owner.getUserRoles().stream().anyMatch(grant -> grant.isActive()
                    && grant.getRole().getCode() == RoleCode.SELLER)
                && status == StorefrontStatus.ACTIVE
                && verificationStatus == VerificationStatus.VERIFIED
                && expiresAt != null
                && expiresAt.isAfter(Instant.now());
    }
    public void reviewVerification(boolean approved) {
        verificationStatus = approved ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED;
    }

    public void invalidateVerification() {
        verificationStatus = VerificationStatus.PENDING;
    }
}
