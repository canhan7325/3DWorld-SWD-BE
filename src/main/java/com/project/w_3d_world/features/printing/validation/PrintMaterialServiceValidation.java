package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintMaterialRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialCostRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialStatusRequest;

import java.math.BigDecimal;
import java.util.Locale;

public final class PrintMaterialServiceValidation {
    private PrintMaterialServiceValidation() {}

    public static void validateTechnologyFilter(String technologyCode) {
        if (technologyCode != null && (technologyCode.isBlank() || technologyCode.length() > 30
                || !technologyCode.equals(technologyCode.strip()))) {
            throw ApiException.invalidRequest("Technology code must have 1 to 30 characters without surrounding whitespace.");
        }
    }

    public static void validatePage(int page, int size) {
        if (page < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (size < 1 || size > 50) throw ApiException.invalidRequest("Page size must be between 1 and 50.");
    }

    public static void validateActor(int actorUserId) {
        if (actorUserId <= 0) throw ApiException.invalidRequest("Authenticated Admin ID is invalid.");
    }

    public static void validateMaterialId(Integer materialId) {
        if (materialId == null || materialId <= 0) {
            throw ApiException.invalidRequest("Material ID must be a positive integer.");
        }
    }

    public static void validateCreateRequest(CreatePrintMaterialRequest request) {
        if (request == null) throw ApiException.invalidRequest("Material request is required.");
    }

    public static void validateCostRequest(UpdatePrintMaterialCostRequest request) {
        if (request == null) throw ApiException.invalidRequest("Cost request is required.");
    }

    public static void validateStatusRequest(UpdatePrintMaterialStatusRequest request) {
        if (request == null || request.isActive() == null) {
            throw ApiException.invalidRequest("isActive must be true or false.");
        }
    }

    public static void validateCost(BigDecimal cost) {
        if (cost == null) return;
        if (cost.signum() < 0 || cost.scale() > 2
                || cost.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw ApiException.invalidRequest("estimatedUnitCost must fit NUMERIC(18,2) and be nonnegative.");
        }
    }

    public static String canonicalTechnology(String raw) {
        try {
            return PrintMaterialCompatibilityPolicy.canonicalTechnology(raw);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRequest(exception.getMessage());
        }
    }

    public static String canonicalName(String raw, String technology) {
        try {
            return PrintMaterialCompatibilityPolicy.canonicalName(raw, technology);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRequest(exception.getMessage());
        }
    }

    public static String canonicalUnit(String raw, String technology) {
        try {
            return PrintMaterialCompatibilityPolicy.canonicalUnit(raw, technology);
        } catch (IllegalArgumentException exception) {
            throw ApiException.invalidRequest(exception.getMessage());
        }
    }

    public static void validateLegacyIdentity(String rawTechnology, String rawUnit,
            String canonicalTechnology, String canonicalUnit) {
        if (!rawTechnology.equals(canonicalTechnology) || !rawUnit.equals(canonicalUnit)) {
            throw ApiException.invalidRequest("Legacy Material identity is not canonical and cannot be reactivated.");
        }
    }

    public static String normalizedName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
