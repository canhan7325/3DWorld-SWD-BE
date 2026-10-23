package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.AdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintMaterialRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialCostRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialStatusRequest;

public interface AdminPrintMaterialService {
    PagedAdminPrintMaterialResponse getMaterials(
            String technologyCode, Boolean isActive, int page, int size);

    AdminPrintMaterialResponse create(int actorUserId, CreatePrintMaterialRequest request);

    AdminPrintMaterialResponse updateCost(
            int actorUserId, int materialId, UpdatePrintMaterialCostRequest request);

    AdminPrintMaterialResponse updateStatus(
            int actorUserId, int materialId, UpdatePrintMaterialStatusRequest request);
}
