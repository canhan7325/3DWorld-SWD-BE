package com.project.w_3d_world.features.model.dto.response;

import com.project.w_3d_world.features.model.enums.ModelFileRole;

public record RestoredModelFileResponse(
        Integer fileId, ModelFileRole fileRole, String fileFormat,
        Long fileSize, boolean isPrimary, String downloadPath) {}
