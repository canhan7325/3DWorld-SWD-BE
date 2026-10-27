package com.project.w_3d_world.features.studio.dto.response;

import java.time.Instant;
import java.util.List;

public record StudioProjectDetailResponse(
        Integer modelId,
        String name,
        String modelType,
        String originType,
        int currentVersion,
        Instant createdAt,
        Instant updatedAt,
        Object scene,
        List<StudioVersionSummary> versions
) {
    public record StudioVersionSummary(
            Integer modelVersionId,
            Integer parentVersionId,
            int versionNumber,
            String sourceType,
            String changeNote,
            Instant createdAt,
            List<StudioFileInfo> files
    ) {}

    public record StudioFileInfo(
            Integer fileId,
            String role,
            String format,
            Long size,
            String downloadUrl
    ) {}
}
