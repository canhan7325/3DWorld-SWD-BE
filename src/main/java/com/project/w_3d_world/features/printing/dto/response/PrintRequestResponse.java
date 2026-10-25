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
        PrintRequestWorkflowResponse workflow) {
    public PrintRequestResponse(Integer printRequestId, String status, String customerNotes,
            Instant createdAt, Instant updatedAt, Instant lockedAt,
            List<PrintRequestItemResponse> items) {
        this(printRequestId, status, customerNotes, createdAt, updatedAt, lockedAt,
                items, null);
    }

    public PrintRequestResponse withWorkflow(PrintRequestWorkflowResponse value) {
        return new PrintRequestResponse(printRequestId, status, customerNotes, createdAt,
                updatedAt, lockedAt, items, value);
    }
}
