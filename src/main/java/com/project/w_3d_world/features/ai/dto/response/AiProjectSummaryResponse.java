package com.project.w_3d_world.features.ai.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiProjectSummaryResponse(
        Integer modelId,
        String modelName,
        String description,
        String visibility,
        int latestVersion,
        Integer primaryFileId,
        String downloadUrl,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> stats,
        String status,
        String thumbnailUrl,
        Integer activeVersionId,
        boolean favorite,
        boolean archived,
        String sourceMode,
        int versionCount,
        List<String> formats
) {
    public AiProjectSummaryResponse(
            Integer modelId,
            String modelName,
            String description,
            String visibility,
            int latestVersion,
            Integer primaryFileId,
            String downloadUrl,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Object> stats
    ) {
        this(modelId, modelName, description, visibility, latestVersion, primaryFileId, downloadUrl,
                createdAt, updatedAt, stats, "READY",
                downloadUrl, null, false, false, "single", latestVersion, List.of("glb"));
    }
}
