package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record StaffProductionBoardItemResponse(
        Integer printRequestId,
        String status,
        Instant queuedAt,
        Integer assignedStaffId,
        Integer currentQuoteId,
        Integer currentFileVersion,
        Integer currentSpecVersion,
        PrintProductionSummaryResponse production) {}
