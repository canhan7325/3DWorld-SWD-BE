package com.project.w_3d_world.features.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModelRequest(
        @NotBlank(message = "Model name is required")
        @Size(max = 255, message = "Model name must not exceed 255 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Model name contains invalid control characters")
        @Schema(example = "Dragon Model")
        String modelName,
        @Size(max = 10_000, message = "Description must not exceed 10000 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Description contains invalid control characters")
        @Schema(example = "Fantasy dragon", nullable = true)
        String description) {}
