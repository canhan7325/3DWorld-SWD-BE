package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class PrintMaterialRequestValidation {
    private PrintMaterialRequestValidation() {}

    public static int requireAdminId(AuthenticatedUser admin) {
        if (admin == null || admin.userId() <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return admin.userId();
    }

    public static String validateTechnology(String technologyCode) {
        if (technologyCode != null && (technologyCode.isBlank() || technologyCode.length() > 30
                || !technologyCode.equals(technologyCode.strip()))) {
            throw ApiException.invalidRequest(
                    "Technology code must have 1 to 30 characters without surrounding whitespace.");
        }
        return technologyCode;
    }

    public static Boolean parseActive(String value) {
        if (value == null) return null;
        if ("true".equals(value)) return Boolean.TRUE;
        if ("false".equals(value)) return Boolean.FALSE;
        throw ApiException.invalidRequest("isActive must be true or false.");
    }

    public static int parsePage(String value) {
        int page = parseInteger(value, "page");
        if (page < 0) {
            throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        }
        return page;
    }

    public static int parsePageSize(String value) {
        int size = parseInteger(value, "size");
        if (size < 1 || size > 50) {
            throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        }
        return size;
    }

    public static int parseMaterialId(String value) {
        int id = parseInteger(value, "materialId");
        if (id <= 0) {
            throw ApiException.invalidRequest("materialId must be a positive integer.");
        }
        return id;
    }

    public static int parseCustomerMaterialId(String value) {
        int id = parseInteger(value, "materialId");
        if (id <= 0) {
            throw ApiException.invalidRequest("Material ID must be a positive integer.");
        }
        return id;
    }

    private static int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw ApiException.invalidRequest(name + " must be a valid integer.");
        }
    }
}
