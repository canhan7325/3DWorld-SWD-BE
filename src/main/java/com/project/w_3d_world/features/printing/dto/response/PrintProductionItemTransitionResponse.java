package com.project.w_3d_world.features.printing.dto.response;

public record PrintProductionItemTransitionResponse(
        Integer printRequestId,
        Integer printRequestItemId,
        String productionStatus,
        PrintProductionSummaryResponse production) {}
