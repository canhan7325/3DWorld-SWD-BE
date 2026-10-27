package com.project.w_3d_world.features.ai.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiVersionDetailResponse(
        Integer modelVersionId,
        Integer modelId,
        Integer parentVersionId,
        Integer versionNumber,
        String sourceType,
        String changeNote,
        Instant createdAt,
        List<AiFileInfo> files,
        Map<String, Object> stats
) {
    public record AiFileInfo(
            Integer fileId,
            String fileRole,
            String fileFormat,
            Long fileSize,
            String downloadUrl
    ) {}
}
