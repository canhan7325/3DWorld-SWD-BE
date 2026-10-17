package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.enums.ModelType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.storage.ModelStorageException;
import com.project.w_3d_world.features.model.storage.StoredModelFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelUploadServiceImpl implements ModelUploadService {
    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;
    private final ModelStorageService storageService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelUploadResponse uploadModel(
            Integer authenticatedUserId, CreateModelRequest request, MultipartFile file) {
        validateRequest(authenticatedUserId, request, file);
        String modelName = request.modelName().trim();
        String description = normalizeDescription(request.description());
        // Register before publication. A mutable holder also covers stream-close failures and
        // failures thrown by Spring after this method returns, during transaction commit.
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Model upload requires an active database transaction.");
        }
        String[] storedKey = {null};
        registerRollbackCleanup(storedKey);

        Model3D model = modelRepository.saveAndFlush(
                new Model3D(authenticatedUserId, modelName, description,
                        ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE"));
        Integer modelId = requiredId(model.getModelId(), "Model ID was not generated.");

        ModelVersion version = versionRepository.saveAndFlush(
                new ModelVersion(modelId, null, null, authenticatedUserId, 1,
                        ModelSourceType.UPLOAD, null));
        Integer versionId = requiredId(version.getModelVersionId(), "Model version ID was not generated.");

        StoredModelFile stored;
        try (InputStream input = file.getInputStream()) {
            stored = storageService.store(input, file.getOriginalFilename(), file.getContentType(),
                    ModelFileRole.SOURCE_MODEL, modelId, versionId);
            storedKey[0] = stored.storageKey();
        } catch (IOException exception) {
            throw new ModelStorageException(ModelStorageException.Code.IO_FAILURE,
                    "Uploaded file could not be read.", exception);
        }

        ModelFile modelFile = fileRepository.saveAndFlush(
                new ModelFile(versionId, ModelFileRole.SOURCE_MODEL, stored.fileFormat(),
                        stored.storageKey(), stored.mimeType(), stored.fileHash(), stored.fileSize(), true));
        Integer fileId = requiredId(modelFile.getFileId(), "Model file ID was not generated.");

        return new ModelUploadResponse(modelId, versionId, fileId, modelName, 1,
                stored.fileFormat(), stored.fileSize(), model.getVisibility(), model.getCreatedAt());
    }

    private void validateRequest(Integer userId, CreateModelRequest request, MultipartFile file) {
        if (userId == null || userId <= 0) {
            throw ApiException.invalidRequest("Authenticated user ID is invalid.");
        }
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

    private void registerRollbackCleanup(String[] storedKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String storageKey = storedKey[0];
                if (storageKey == null) return;
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storageService.deleteForCleanup(storageKey);
                    } catch (RuntimeException exception) {
                        // Storage keys are server-generated relative identifiers. Do not log the
                        // exception/cause, which could contain OS paths or connection information.
                        log.error("Model file cleanup failed; reconcile key={} errorType={}",
                                storageKey, exception.getClass().getSimpleName());
                    }
                } else if (status == STATUS_UNKNOWN) {
                    log.error("Model upload completion is unknown; retain and reconcile key={}", storageKey);
                }
            }
        });
    }

    private static String normalizeDescription(String description) {
        if (description == null) return null;
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Integer requiredId(Integer id, String message) {
        return Objects.requireNonNull(id, message);
    }
}
