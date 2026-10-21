package com.project.w_3d_world.features.model.validation;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class ModelRequestValidation {
    private ModelRequestValidation() {}

    public static int requireUserId(AuthenticatedUser user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return user.userId();
    }

    public static int parsePage(String value) {
        int page = parseInteger(value, "page");
        if (page < 0) {
            throw ApiException.invalidRequest("page must be greater than or equal to 0.");
        }
        return page;
    }

    public static int parsePageSize(String value) {
        int size = parsePositive(value, "size");
        if (size > 50) {
            throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        }
        return size;
    }

    public static int parsePositive(String value, String name) {
        int parsed = parseInteger(value, name);
        if (parsed <= 0) {
            throw ApiException.invalidRequest(name + " must be a positive integer.");
        }
        return parsed;
    }

    public static int parseDownloadId(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Download has historically used one message for malformed and nonpositive IDs.
        }
        throw ApiException.invalidRequest(name + " must be a positive integer.");
    }

    private static int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw ApiException.invalidRequest(name + " must be a valid integer.");
        }
    }
}
