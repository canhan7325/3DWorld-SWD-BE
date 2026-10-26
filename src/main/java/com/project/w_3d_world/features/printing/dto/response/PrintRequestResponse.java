package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;
import java.util.List;

public record PrintRequestResponse(
        Integer printRequestId,
        String status,
        String customerNotes,
        Instant createdAt,
        Instant updatedAt,
        Instant lockedAt,
        List<PrintRequestItemResponse> items,
        PrintRequestWorkflowResponse workflow,
        PrintProductionSummaryResponse production,
        PrintRequestShippingReadinessResponse shipping) {
    public PrintRequestResponse(Integer printRequestId, String status, String customerNotes,
            Instant createdAt, Instant updatedAt, Instant lockedAt,
            List<PrintRequestItemResponse> items) {
        this(printRequestId, status, customerNotes, createdAt, updatedAt, lockedAt,
                items, null, null, null);
    }

    public PrintRequestResponse(Integer printRequestId, String status, String customerNotes,
            Instant createdAt, Instant updatedAt, Instant lockedAt,
            List<PrintRequestItemResponse> items, PrintRequestWorkflowResponse workflow) {
        this(printRequestId, status, customerNotes, createdAt, updatedAt, lockedAt,
                items, workflow, null, null);
    }

    public PrintRequestResponse(Integer printRequestId, String status, String customerNotes,
            Instant createdAt, Instant updatedAt, Instant lockedAt,
            List<PrintRequestItemResponse> items, PrintRequestWorkflowResponse workflow,
            PrintProductionSummaryResponse production) {
        this(printRequestId, status, customerNotes, createdAt, updatedAt, lockedAt,
                items, workflow, production, null);
    }

    public PrintRequestResponse withWorkflow(PrintRequestWorkflowResponse value) {
        return new PrintRequestResponse(printRequestId, status, customerNotes, createdAt,
                updatedAt, lockedAt, items, value, production, shipping);
    }

    public PrintRequestResponse withProduction(PrintProductionSummaryResponse value) {
        return new PrintRequestResponse(printRequestId, status, customerNotes, createdAt,
                updatedAt, lockedAt, items, workflow, value, shipping);
    }

    public PrintRequestResponse withShipping(PrintRequestShippingReadinessResponse value) {
        return new PrintRequestResponse(printRequestId, status, customerNotes, createdAt,
                updatedAt, lockedAt, items, workflow, production, value);
    }
}
