package com.project.w_3d_world.features.audit.entity;

import com.project.w_3d_world.common.util.JsonValues;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

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
        this.metadata = JsonValues.normalizeMetadata(metadata);
    }
}
