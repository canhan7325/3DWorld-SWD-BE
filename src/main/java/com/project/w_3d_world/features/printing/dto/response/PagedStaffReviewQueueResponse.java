package com.project.w_3d_world.features.printing.dto.response;

import java.util.List;

public record PagedStaffReviewQueueResponse(
        List<StaffReviewQueueItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
