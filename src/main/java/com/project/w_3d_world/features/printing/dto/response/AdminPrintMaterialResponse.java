package com.project.w_3d_world.features.printing.dto.response;

import java.math.BigDecimal;

public record AdminPrintMaterialResponse(
        Integer materialId,
        String materialName,
        String technologyCode,
        String unit,
        BigDecimal estimatedUnitCost,
        Boolean isActive) {}
