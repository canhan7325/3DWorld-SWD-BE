package com.project.w_3d_world.features.ai.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateAiProjectRequest(
        @Size(max = 255, message = "Name cannot exceed 255 characters.")
        String name,
        String description,
        String visibility,
        Boolean favorite,
        Boolean archived,
        Integer activeVersionId
) {
    public UpdateAiProjectRequest(String name, String description, String visibility) {
        this(name, description, visibility, null, null, null);
    }
}
