package com.project.w_3d_world.features.studio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveStudioContentRequest(
        @NotNull(message = "baseRevision is required.")
        @Min(value = 1, message = "baseRevision must be at least 1.")
        Integer baseRevision,
        Object scene,
        String note
) {}
