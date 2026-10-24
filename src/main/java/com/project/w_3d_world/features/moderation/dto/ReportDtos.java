package com.project.w_3d_world.features.moderation.dto;

import com.project.w_3d_world.features.moderation.entity.Report;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ReportDtos {
    private ReportDtos() {}

    public record CreateRequest(@Positive Integer productId, @Positive Integer reportedUserId,
            @NotBlank @Size(max = 60) String reason, @Size(max = 10000) String description,
            @Size(max = 2000) String evidenceKey) {}
    public record ResolveRequest(@NotBlank String status, @NotBlank @Size(max = 1000) String reason,
                                 boolean hideProduct) {}
    public record ReportResponse(int id, Integer productId, Integer reportedUserId, String reason,
            String description, String evidenceKey, String status, String decisionReason, Instant createdAt,
            Instant resolvedAt) {
        public static ReportResponse from(Report report) {
            return new ReportResponse(report.getId(), report.getProductId(), report.getReportedUserId(),
                    report.getReportReason(), report.getDescription(), report.getEvidenceKey(), report.getStatus(),
                    report.getDecisionReason(), report.getCreatedAt(), report.getResolvedAt());
        }
    }
}
