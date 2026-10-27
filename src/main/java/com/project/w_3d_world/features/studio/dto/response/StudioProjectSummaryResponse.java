package com.project.w_3d_world.features.studio.dto.response;

import java.time.Instant;

public record StudioProjectSummaryResponse(
        Integer modelId,
        String name,
        String modelType,
        String originType,
        int latestVersion,
        Instant createdAt,
        Instant updatedAt
) {}
