package com.project.w_3d_world.features.auth.entity;

import com.project.w_3d_world.features.auth.enums.ApprovalStatus;
import com.project.w_3d_world.features.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {
    @EmbeddedId private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.ACTIVE;

    @Column(name = "granted_by_user_id")
    private Integer grantedByUserId;

    @Column(name = "granted_at")
    private Instant grantedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UserRole(User user, Role role) {
        this.id = new UserRoleId(user.getId(), role.getId());
        this.user = user;
        this.role = role;
    }

    public boolean isActive() {
        return approvalStatus == ApprovalStatus.ACTIVE;
    }
    public void changeApproval(boolean active, int actorId) {
        approvalStatus = active ? ApprovalStatus.ACTIVE : ApprovalStatus.REVOKED;
        grantedByUserId = actorId;
        grantedAt = Instant.now();
    }
}
