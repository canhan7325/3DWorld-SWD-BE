package com.project.w_3d_world.features.ai.dto.response;

import java.util.Map;

public record AiDimensionsResponse(
        Integer modelVersionId,
        Map<String, Object> computedDimensions,
        Map<String, Object> boundingBox,
        Map<String, Object> meshStats,
        String note
) {}
