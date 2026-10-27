package com.project.w_3d_world.features.ai.dto;

import jakarta.validation.constraints.Size;

public record UpdateAiProjectRequest(
        @Size(max = 255, message = "Name cannot exceed 255 characters.")
        String name,
        String description,
        String visibility
) {}
