package com.project.w_3d_world.features.model.dto.response;

import com.project.w_3d_world.features.model.enums.ModelSourceType;

import java.time.Instant;
import java.util.List;

public record RestoreModelVersionResponse(
        Integer modelId, Integer modelVersionId, Integer versionNumber,
        Integer parentVersionId, ModelSourceType sourceType, String changeNote,
        Instant createdAt, List<RestoredModelFileResponse> files) {}
