package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;

import java.math.BigDecimal;
import java.util.List;

public final class PrintRequestCreateValidation {
    private static final BigDecimal MAX_DIMENSION = new BigDecimal("9999999999.99");

    private PrintRequestCreateValidation() {}

    public static void validate(CreatePrintRequestRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.rightsDeclarationAccepted())) {
            throw ApiException.invalidRequest("rightsDeclarationAccepted must be true.");
        }
        validateSpecification(request.customerNotes(), request.items());
    }

    public static void validateSpecification(
            String customerNotes, List<CreatePrintRequestItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw ApiException.invalidRequest("At least one Print Request item is required.");
        }
        validateText(customerNotes, "customerNotes", 10_000);
        Integer requestModelVersionId = null;
        for (CreatePrintRequestItemRequest item : items) {
            if (item == null) throw ApiException.invalidRequest("Print Request item is required.");
            if (item.modelVersionId() == null || item.modelVersionId() <= 0) {
                throw ApiException.invalidRequest("modelVersionId must be a positive integer.");
            }
            if (requestModelVersionId == null) {
                requestModelVersionId = item.modelVersionId();
            } else if (!requestModelVersionId.equals(item.modelVersionId())) {
                throw ApiException.invalidRequest("All Print Request items must use the same Model Version.");
            }
            if (item.materialId() == null || item.materialId() <= 0) {
                throw ApiException.invalidRequest("materialId must be a positive integer.");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw ApiException.invalidRequest("quantity must be a positive integer.");
            }
            if (item.infillPercent() != null && (item.infillPercent() < 0 || item.infillPercent() > 100)) {
                throw ApiException.invalidRequest("infillPercent must be between 0 and 100.");
            }
            dimension(item.widthMm(), "widthMm");
            dimension(item.heightMm(), "heightMm");
            dimension(item.depthMm(), "depthMm");
            validateText(item.color(), "color", 100);
            validateText(item.quality(), "quality", 40);
            validateText(item.notes(), "notes", 10_000);
        }
    }

    public static void validateInfill(String technology, Integer infillPercent) {
        if ("SLA".equals(technology) && infillPercent != null) {
            throw ApiException.invalidRequest("infillPercent is not applicable to SLA.");
        }
    }

    private static void dimension(BigDecimal value, String name) {
        if (value != null && (value.signum() <= 0 || value.scale() > 2
                || value.compareTo(MAX_DIMENSION) > 0)) {
            throw ApiException.invalidRequest(name + " must be positive and fit NUMERIC(12,2).");
        }
    }

    private static void validateText(String value, String name, int maxLength) {
        if (value != null && (value.length() > maxLength
                || value.chars().anyMatch(Character::isISOControl))) {
            throw ApiException.invalidRequest(name + " contains invalid characters or is too long.");
        }
    }
}
