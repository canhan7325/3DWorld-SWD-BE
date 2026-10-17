package com.project.w_3d_world.features.model.dto.response;

import java.time.Instant;

public record ModelUploadResponse(
        Integer modelId,
        Integer modelVersionId,
        Integer fileId,
        String modelName,
        Integer versionNumber,
        String fileFormat,
        Long fileSize,
        String visibility,
        Instant createdAt) {}
