package com.project.w_3d_world.features.studio.dto.response;

import java.time.Instant;

public record StudioSnapshotResponse(
        String snapshotCode,
        Integer modelId,
        Integer versionNumber,
        String changeNote,
        String fileFormat,
        Long fileSize,
        Instant createdAt,
        String downloadUrl
) {}
