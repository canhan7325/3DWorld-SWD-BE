package com.project.w_3d_world.features.model.validation;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

public final class ModelServiceValidation {
    private ModelServiceValidation() {}

    public static void validateUploadRequest(Integer userId, CreateModelRequest request, MultipartFile file) {
        validateUser(userId);
        if (request == null || request.modelName() == null || request.modelName().trim().isEmpty()) {
            throw ApiException.invalidRequest("Model name is required.");
        }
        if (request.modelName().length() > 255) {
            throw ApiException.invalidRequest("Model name must not exceed 255 characters.");
        }
        if (request.modelName().chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalidRequest("Model name contains invalid control characters.");
        }
        if (request.description() != null && request.description().chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalidRequest("Description contains invalid control characters.");
        }
        if (request.description() != null && request.description().length() > 10_000) {
            throw ApiException.invalidRequest("Description must not exceed 10000 characters.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.invalidRequest("A non-empty model file is required.");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw ApiException.invalidRequest("The uploaded file must have a filename.");
        }
    }

    public static void validateUser(Integer userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.invalidRequest("Authenticated user ID is invalid.");
        }
    }

    public static void validatePositive(Integer value, String name) {
        if (value == null || value <= 0) {
            throw ApiException.invalidRequest(name + " must be a positive integer.");
        }
    }

    public static void validatePage(int page, int size) {
        if (page < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (size < 1 || size > 50) {
            throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        }
    }

    public static String normalizeDescription(String description) {
        if (description == null) return null;
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeFileFormat(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
