package com.project.w_3d_world.features.ai.dto;

import java.util.Map;

public record AiEstimateResponse(
        int perVariant,
        int variants,
        int total,
        int userBalance,
        boolean sufficient,
        String priceVersion,
        Map<String, Object> config
) {}
