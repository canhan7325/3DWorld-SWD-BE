package com.project.w_3d_world.features.printing.dto.response;

public record PrintMaterialResponse(
        Integer materialId,
        String materialName,
        String technologyCode,
        String unit) {}
