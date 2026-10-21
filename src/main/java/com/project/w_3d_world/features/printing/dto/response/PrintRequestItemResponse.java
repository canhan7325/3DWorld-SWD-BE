package com.project.w_3d_world.features.printing.dto.response;

import java.math.BigDecimal;

public record PrintRequestItemResponse(
        Integer printRequestItemId,
        Integer materialId,
        Integer modelVersionId,
        String sourceType,
        String color,
        String quality,
        Integer infillPercent,
        BigDecimal widthMm,
        BigDecimal heightMm,
        BigDecimal depthMm,
        Integer quantity,
        String notes,
        String productionStatus) {}
