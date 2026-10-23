package com.project.w_3d_world.features.model.dto.response;

import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelType;

import java.time.Instant;

public record ModelDetailResponse(
        Integer modelId,
        String modelName,
        String description,
        ModelType modelType,
        ModelOriginType originType,
        String visibility,
        LatestModelVersionResponse latestVersion,
        Instant createdAt,
        Instant updatedAt) {}
