package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record PrintRequestWorkflowResponse(
        String displayStatus,
        Instant quoteDeadline,
        Instant paymentExpiresAt,
        Integer remainingRevisions,
        Integer currentFileVersion,
        Integer currentSpecVersion,
        AllowedActions allowedActions) {

    public record AllowedActions(
            boolean editFile, boolean editSpecification, boolean confirmPreview,
            boolean submit, boolean requestRevision, boolean acceptQuote,
            boolean pay, boolean cancel) {}
}
