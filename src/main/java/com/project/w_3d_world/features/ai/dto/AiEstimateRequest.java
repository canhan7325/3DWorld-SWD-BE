package com.project.w_3d_world.features.ai.dto;

import java.util.Map;

public record AiEstimateRequest(
        Map<String, Object> config,
        Integer variants,
        String mode
) {}
