package com.project.w_3d_world.features.ai.dto.response;

import java.util.List;
import java.util.Map;

public record AiConfigOptionsResponse(
        String defaultProvider,
        String priceVersion,
        List<Map<String, Object>> models,
        Map<String, Map<String, String>> presets,
        Map<String, Object> uploadLimits,
        List<Map<String, Object>> pricing
) {
    public AiConfigOptionsResponse(
            String defaultProvider,
            String priceVersion,
            List<Map<String, Object>> models,
            Map<String, Map<String, String>> presets,
            Map<String, Object> uploadLimits
    ) {
        this(defaultProvider, priceVersion, models, presets, uploadLimits, List.of(
                Map.of("label", "Single Image Draft", "aiModel", "latest", "texture", false, "credits", 20),
                Map.of("label", "Single Image Textured", "aiModel", "latest", "texture", true, "credits", 30),
                Map.of("label", "Multi-View Detailed", "aiModel", "latest", "texture", true, "credits", 50)
        ));
    }
}
