package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record PrintRequestFileVersionResponse(
        Integer printRequestId, Integer version, String fileName,
        Long fileSize, String fileType, Instant uploadedAt,
        boolean current, String downloadPath) {}
