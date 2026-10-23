package com.project.w_3d_world.features.model.dto.response;

import com.project.w_3d_world.features.model.enums.ModelSourceType;

public record LatestModelVersionResponse(
        Integer modelVersionId, Integer versionNumber, ModelSourceType sourceType) {}
