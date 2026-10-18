package com.project.w_3d_world.features.model.dto.response;

import java.util.List;

public record PagedModelResponse(
        List<ModelSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
