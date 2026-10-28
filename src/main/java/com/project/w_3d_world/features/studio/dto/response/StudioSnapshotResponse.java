package com.project.w_3d_world.features.studio.dto.response;

import java.time.Instant;
import java.util.List;

public record StudioSnapshotResponse(
        String snapshotCode,
        Integer modelId,
        Integer versionNumber,
        String changeNote,
        String fileFormat,
        Long fileSize,
        Instant createdAt,
        String downloadUrl,
        String id,
        Integer revision,
        String meshHash,
        List<Double> dims,
        Integer parts,
        Integer triangles,
        String state,
        String unit,
        Long bytes,
        List<String> acknowledgedWarnings,
        String consentVersion
) {
    public StudioSnapshotResponse(
            String snapshotCode,
            Integer modelId,
            Integer versionNumber,
            String changeNote,
            String fileFormat,
            Long fileSize,
            Instant createdAt,
            String downloadUrl
    ) {
        this(
                snapshotCode, modelId, versionNumber, changeNote, fileFormat, fileSize, createdAt, downloadUrl,
                snapshotCode, versionNumber, null, null, null, null, "READY", "mm", fileSize, List.of(), null
        );
    }
}
