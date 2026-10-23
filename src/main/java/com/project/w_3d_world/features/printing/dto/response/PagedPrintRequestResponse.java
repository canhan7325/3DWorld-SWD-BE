package com.project.w_3d_world.features.printing.dto.response;

import java.util.List;

public record PagedPrintRequestResponse(
        List<PrintRequestSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
