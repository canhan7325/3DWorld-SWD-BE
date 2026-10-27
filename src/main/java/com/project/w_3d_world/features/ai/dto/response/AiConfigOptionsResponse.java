package com.project.w_3d_world.features.ai.dto.response;

import java.util.List;
import java.util.Map;

public record AiConfigOptionsResponse(
        String defaultProvider,
        String priceVersion,
        List<Map<String, Object>> models,
        Map<String, Map<String, String>> presets,
        Map<String, Object> uploadLimits
) {}
