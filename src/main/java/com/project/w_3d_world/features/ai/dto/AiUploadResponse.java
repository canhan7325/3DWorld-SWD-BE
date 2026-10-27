package com.project.w_3d_world.features.ai.dto;

import java.time.Instant;

public record AiUploadResponse(
        String uploadId,
        String storageKey,
        String fileName,
        Long fileSize,
        String mimeType,
        String downloadUrl,
        Instant createdAt
) {}
