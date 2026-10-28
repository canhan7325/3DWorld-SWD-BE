package com.project.w_3d_world.features.ai.model;

import java.time.Instant;

public record StagedUpload(
        String uploadId,
        int userId,
        String filename,
        String mimeType,
        long fileSize,
        byte[] data,
        Instant createdAt
) {}
