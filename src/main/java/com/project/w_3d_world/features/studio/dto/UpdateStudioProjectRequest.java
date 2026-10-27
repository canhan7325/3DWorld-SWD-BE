package com.project.w_3d_world.features.studio.dto;

import jakarta.validation.constraints.Size;

public record UpdateStudioProjectRequest(
        @Size(max = 255, message = "Name cannot exceed 255 characters.")
        String name,
        String description
) {}
