package com.project.w_3d_world.features.studio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SaveStudioProjectRequest(
        @Min(value = 1, message = "expectedVersion must be at least 1")
        @Schema(description = "The version number you are modifying; prevents accidental overwrites", example = "1")
        int expectedVersion,

        @Size(max = 500, message = "Change note cannot exceed 500 characters")
        @Schema(description = "Optional change description", example = "Added armrests and adjusted scale")
        String note,

        @Schema(description = "Updated scene graph JSON")
        Object scene
) {}
