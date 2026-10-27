package com.project.w_3d_world.features.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record GenerateTextRequest(
        @NotBlank(message = "Prompt cannot be blank")
        @Size(max = 800, message = "Prompt cannot exceed 800 characters")
        @Schema(description = "Text description to generate 3D model", example = "A detailed vintage wooden treasure chest with brass locks")
        String prompt,

        @Size(max = 255, message = "Name cannot exceed 255 characters")
        @Schema(description = "Optional model name", example = "Vintage Treasure Chest")
        String name,

        @Schema(description = "Optional configuration parameters")
        Map<String, Object> config
) {}
