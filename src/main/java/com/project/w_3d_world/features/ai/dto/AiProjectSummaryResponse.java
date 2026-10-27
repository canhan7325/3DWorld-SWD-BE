package com.project.w_3d_world.features.ai.dto;

import java.time.Instant;
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
        Map<String, Object> stats
) {}
