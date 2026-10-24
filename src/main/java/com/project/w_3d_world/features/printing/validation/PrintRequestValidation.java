package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class PrintRequestValidation {
    private PrintRequestValidation() {}

    public static int requireCustomerId(AuthenticatedUser customer) {
        if (customer == null || customer.userId() <= 0) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return customer.userId();
    }

    public static int parsePage(String value) {
        int page = parseInteger(value, "page");
        if (page < 0) {
            throw ApiException.invalidRequest("page must be greater than or equal to 0.");
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

    public static int parsePrintRequestId(String value) {
        int id = parseInteger(value, "printRequestId");
        if (id <= 0) {
            throw ApiException.invalidRequest("printRequestId must be a positive integer.");
        }
        return id;
    }

    public static int parseFileVersion(String value) {
        int version = parseInteger(value, "version");
        if (version <= 0) throw ApiException.invalidRequest("version must be a positive integer.");
        return version;
    }

    public static void validateCustomerId(Integer customerUserId) {
        if (customerUserId == null || customerUserId <= 0) {
            throw ApiException.invalidRequest("Authenticated customer ID is invalid.");
        }
    }

    public static void validatePage(int page, int size) {
        if (page < 0) {
            throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        }
        if (size < 1 || size > 50) {
            throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        }
    }

    public static void validatePrintRequestId(Integer printRequestId) {
        if (printRequestId == null || printRequestId <= 0) {
            throw ApiException.invalidRequest("Print Request ID must be a positive integer.");
        }
    }

    private static int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw ApiException.invalidRequest(name + " must be a valid integer.");
        }
    }
}
