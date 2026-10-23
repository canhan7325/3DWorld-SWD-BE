package com.project.w_3d_world.features.printing.dto.response;

import java.util.List;

public record PagedAdminPrintMaterialResponse(
        List<AdminPrintMaterialResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
