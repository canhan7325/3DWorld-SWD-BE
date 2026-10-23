package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.features.printing.dto.response.PagedPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintMaterialResponse;

public interface PrintMaterialService {
    PagedPrintMaterialResponse getActiveMaterials(String technologyCode, int page, int size);

    PrintMaterialResponse getActiveMaterialById(Integer materialId);
}
