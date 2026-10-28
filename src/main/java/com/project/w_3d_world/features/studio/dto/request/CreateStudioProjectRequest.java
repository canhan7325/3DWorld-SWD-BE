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
        Object scene,

        @Schema(description = "Optional thumbnail base64 data URL (PNG/JPEG) under 2MB")
        String thumbnail,

        String unit,
        String preset,
        String templateId,
        String icon
) {
    public CreateStudioProjectRequest(String name, Object scene) {
        this(name, scene, null, "mm", null, null, null);
    }
}
