package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;
import java.util.List;

public record StaffProductionDetailResponse(
        Integer printRequestId,
        String status,
        Instant queuedAt,
        Integer assignedStaffId,
        Integer currentQuoteId,
        Integer currentFileVersion,
        Integer currentSpecVersion,
        PrintProductionSummaryResponse production,
        List<StaffProductionItemResponse> items,
        List<PrintQuoteItemResponse> quoteItems,
        PrintRequestShippingReadinessResponse shipping) {
    public StaffProductionDetailResponse(Integer printRequestId, String status, Instant queuedAt,
            Integer assignedStaffId, Integer currentQuoteId, Integer currentFileVersion,
            Integer currentSpecVersion, PrintProductionSummaryResponse production,
            List<StaffProductionItemResponse> items, List<PrintQuoteItemResponse> quoteItems) {
        this(printRequestId, status, queuedAt, assignedStaffId, currentQuoteId,
                currentFileVersion, currentSpecVersion, production, items, quoteItems, null);
    }

    public StaffProductionDetailResponse withShipping(PrintRequestShippingReadinessResponse value) {
        return new StaffProductionDetailResponse(printRequestId, status, queuedAt,
                assignedStaffId, currentQuoteId, currentFileVersion, currentSpecVersion,
                production, items, quoteItems, value);
    }
}
