package com.project.w_3d_world.features.ai.dto.response;

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
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant completedAt,
        List<VersionSummary> versions,
        Map<String, Object> stats,
        Map<String, Object> config,
        List<String> imageIds,
        Integer variantIndex
) {
    public AiTaskDetailResponse(
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
        this(
                aiRequestId, modelId, modelName, requestType, actionType, status, progress,
                providerName, providerJobReference, creditsUsed, failureReason,
                failureReason != null ? "TASK_FAILED" : null, failureReason,
                createdAt, completedAt, versions, stats,
                Map.of(), List.of(), 0
        );
    }

    public record VersionSummary(
            Integer modelVersionId,
            int versionNumber,
            String sourceType,
            Instant createdAt,
            Integer fileId,
            String glbDownloadUrl
    ) {}
}
