package com.project.w_3d_world.features.moderation.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Integer id;
    @Column(name = "reporter_user_id", nullable = false)
    private Integer reporterUserId;
    @Column(name = "product_id")
    private Integer productId;
    @Column(name = "reported_user_id")
    private Integer reportedUserId;
    @Column(name = "report_reason", nullable = false, length = 60)
    private String reportReason;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "evidence_key", columnDefinition = "text")
    private String evidenceKey;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(name = "handled_by_user_id")
    private Integer handledByUserId;
    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Report(int reporter, Integer product, Integer reportedUser, String reason, String description, String evidence) {
        reporterUserId = reporter;
        productId = product;
        reportedUserId = reportedUser;
        reportReason = reason;
        this.description = description;
        evidenceKey = evidence;
    }

    public void resolve(String status, int actor, String reason) {
        this.status = status;
        handledByUserId = actor;
        decisionReason = reason;
        resolvedAt = Instant.now();
    }
}
