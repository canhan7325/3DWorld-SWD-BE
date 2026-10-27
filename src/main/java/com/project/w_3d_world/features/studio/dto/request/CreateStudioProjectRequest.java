package com.project.w_3d_world.features.studio.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStudioProjectRequest(
        @NotBlank(message = "Project name cannot be blank")
        @Size(max = 255, message = "Project name cannot exceed 255 characters")
        @Schema(description = "Name of the 3D Studio project", example = "Modern Chair Design")
        String name,

        @Schema(description = "Initial scene graph data (nodes, meshes, lights, camera)")
        Object scene
) {}
