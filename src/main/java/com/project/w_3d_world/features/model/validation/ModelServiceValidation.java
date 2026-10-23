package com.project.w_3d_world.features.model.validation;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

public final class ModelServiceValidation {
    private ModelServiceValidation() {}

    public static String modelNameFromFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.chars().anyMatch(Character::isISOControl)
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")) {
            throw ApiException.invalidRequest("A plain model filename is required.");
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw ApiException.invalidRequest("A model filename with an extension is required.");
        }
        return filename.substring(0, dot);
    }

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

    public static void validateSaveRequest(
            Integer userId, Integer modelId, SaveModelVersionRequest request, MultipartFile file) {
        validateUser(userId);
        validatePositive(modelId, "Model ID");
        if (request == null || request.baseVersionId() == null || request.baseVersionId() <= 0) {
            throw ApiException.invalidRequest("baseVersionId must be a positive integer.");
        }
        if (request.changeNote() != null && (request.changeNote().length() > 10_000
                || request.changeNote().chars().anyMatch(Character::isISOControl))) {
            throw ApiException.invalidRequest("changeNote contains invalid characters or is too long.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.invalidRequest("A non-empty model file is required.");
        }
        modelNameFromFilename(file.getOriginalFilename());
    }

    public static void validateRestoreRequest(
            Integer userId, Integer modelId, Integer versionId,
            RestoreModelVersionRequest request) {
        validateUser(userId);
        validatePositive(modelId, "Model ID");
        validatePositive(versionId, "Version ID");
        if (request == null || request.baseVersionId() == null || request.baseVersionId() <= 0) {
            throw ApiException.invalidRequest("baseVersionId must be a positive integer.");
        }
        if (request.changeNote() != null && (request.changeNote().length() > 10_000
                || request.changeNote().chars().anyMatch(Character::isISOControl))) {
            throw ApiException.invalidRequest("changeNote contains invalid characters or is too long.");
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
