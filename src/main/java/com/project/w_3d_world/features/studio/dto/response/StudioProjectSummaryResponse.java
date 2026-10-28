package com.project.w_3d_world.features.studio.dto.response;

import java.time.Instant;

public record StudioProjectSummaryResponse(
        Integer modelId,
        String name,
        String modelType,
        String originType,
        int latestVersion,
        Instant createdAt,
        Instant updatedAt,
        String status,
        String unit,
        String preset,
        String templateId,
        String icon,
        int revision,
        String thumbnailUrl,
        String thumbnail
) {
    public StudioProjectSummaryResponse(
            Integer modelId,
            String name,
            String modelType,
            String originType,
            int latestVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(modelId, name, modelType, originType, latestVersion, createdAt, updatedAt,
                "saved", "mm", null, null, null, latestVersion,
                "/api/studio/projects/" + modelId + "/thumbnail",
                "/api/studio/projects/" + modelId + "/thumbnail");
    }
}
