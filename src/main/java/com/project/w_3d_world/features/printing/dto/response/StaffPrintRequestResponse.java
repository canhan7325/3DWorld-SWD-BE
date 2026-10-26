package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record StaffPrintRequestResponse(
        Integer printRequestId,
        String status,
        Instant submittedAt,
        Integer currentFileVersion,
        Integer currentSpecVersion,
        PrintRequestFileVersionResponse currentFile,
        JsonNode currentSpecification,
        List<PrintRequestItemResponse> items,
        List<PrintRequestTechnicalReviewResponse> reviews,
        Integer assignedStaffId,
        Instant handledAt) {
    public StaffPrintRequestResponse(Integer printRequestId, String status, Instant submittedAt,
            Integer currentFileVersion, Integer currentSpecVersion,
            PrintRequestFileVersionResponse currentFile, JsonNode currentSpecification,
            List<PrintRequestItemResponse> items, List<PrintRequestTechnicalReviewResponse> reviews) {
        this(printRequestId, status, submittedAt, currentFileVersion, currentSpecVersion,
                currentFile, currentSpecification, items, reviews, null, null);
    }
}
