package com.project.w_3d_world.features.ai.dto.response;

import java.time.Instant;

public record AiExportResponse(
        Integer fileId,
        String format,
        Long fileSize,
        String downloadUrl,
        String fileHash,
        Instant exportedAt
) {}
