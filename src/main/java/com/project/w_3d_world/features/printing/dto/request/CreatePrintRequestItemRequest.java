package com.project.w_3d_world.features.printing.dto.request;

import java.math.BigDecimal;

public record CreatePrintRequestItemRequest(
        Integer modelVersionId, Integer materialId, String color, String quality,
        Integer infillPercent, BigDecimal widthMm, BigDecimal heightMm, BigDecimal depthMm,
        Integer quantity, String notes) {}
