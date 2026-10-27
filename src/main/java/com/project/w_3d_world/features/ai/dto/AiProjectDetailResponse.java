package com.project.w_3d_world.features.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiProjectDetailResponse(
        Integer modelId,
        String modelName,
        String description,
        String visibility,
        Instant createdAt,
        Instant updatedAt,
        List<AiTaskDetailResponse.VersionSummary> versions,
        List<AiTaskSummary> tasks,
        Map<String, Object> stats
) {
    public record AiTaskSummary(
            Integer aiRequestId,
            String requestType,
            String actionType,
            String status,
            String prompt,
            String providerName,
            int creditsUsed,
            Instant createdAt,
            Instant completedAt
    ) {}
}
