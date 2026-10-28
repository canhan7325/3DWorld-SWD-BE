package com.project.w_3d_world.features.studio.dto.request;

import java.util.List;

public record CreateStudioSnapshotRequest(
        String note,
        String format,
        Integer revision,
        String meshHash,
        List<Double> dims,
        Integer parts,
        Integer triangles,
        List<String> acknowledgedWarnings,
        String consentVersion,
        String unit
) {
    public CreateStudioSnapshotRequest(String note, String format) {
        this(note, format, null, null, null, null, null, null, null, "mm");
    }
}
