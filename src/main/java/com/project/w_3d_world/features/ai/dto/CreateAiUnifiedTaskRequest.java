package com.project.w_3d_world.features.ai.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateAiUnifiedTaskRequest(
        String mode,
        @Size(max = 255, message = "Name cannot exceed 255 characters.")
        String name,
        @Size(max = 800, message = "Prompt cannot exceed 800 characters.")
        String prompt,
        Map<String, Object> config,
        Integer variants
) {}
