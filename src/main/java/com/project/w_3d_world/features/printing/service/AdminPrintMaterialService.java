package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;

public interface AdminPrintMaterialService {
    PagedAdminPrintMaterialResponse getMaterials(
            String technologyCode, Boolean isActive, int page, int size);
}
