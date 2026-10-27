package com.project.w_3d_world.features.ai.dto;

public record AiDimensionsRequest(
        Double widthMm,
        Double heightMm,
        Double depthMm,
        String unit
) {}
