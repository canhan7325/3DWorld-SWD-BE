package com.project.w_3d_world.features.audit.entity;

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
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Integer id;

    @Column(name = "actor_user_id")
    private Integer actorUserId;

    @Column(name = "action_type", nullable = false, length = 60)
    private String actionType;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Integer entityId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(columnDefinition = "text")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLog(
            Integer actorUserId,
            String actionType,
            String entityType,
            Integer entityId,
            String reason,
            String metadata) {
        this.actorUserId = actorUserId;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reason = reason;
        this.metadata = metadata;
    }
}
