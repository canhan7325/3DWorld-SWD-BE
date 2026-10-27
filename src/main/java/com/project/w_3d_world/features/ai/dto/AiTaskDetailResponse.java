package com.project.w_3d_world.features.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiTaskDetailResponse(
        Integer aiRequestId,
        Integer modelId,
        String modelName,
        String requestType,
        String actionType,
        String status,
        int progress,
        String providerName,
        String providerJobReference,
        int creditsUsed,
        String failureReason,
        Instant createdAt,
        Instant completedAt,
        List<VersionSummary> versions,
        Map<String, Object> stats
) {
    public record VersionSummary(
            Integer modelVersionId,
            int versionNumber,
            String sourceType,
            Instant createdAt,
            Integer fileId,
            String glbDownloadUrl
    ) {}
}
