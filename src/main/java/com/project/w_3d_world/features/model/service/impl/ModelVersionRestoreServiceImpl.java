package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.RestoreModelVersionResponse;
import com.project.w_3d_world.features.model.dto.response.RestoredModelFileResponse;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.service.ModelVersionRestoreService;
import com.project.w_3d_world.features.model.storage.ModelFileValidator;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelVersionRestoreServiceImpl implements ModelVersionRestoreService {
    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;
    private final ModelStorageService storageService;

    @Override
    @Transactional
    public RestoreModelVersionResponse restoreVersion(
            Integer authenticatedUserId, Integer modelId, Integer versionId,
            RestoreModelVersionRequest request) {
        ModelServiceValidation.validateRestoreRequest(authenticatedUserId, modelId, versionId, request);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Model restore requires an active database transaction.");
        }

        modelRepository.findOwnedActiveByIdForUpdate(modelId, authenticatedUserId)
                .orElseThrow(ModelVersionRestoreServiceImpl::notFound);
        ModelVersion source = versionRepository.findByModelVersionIdAndModelId(versionId, modelId)
                .orElseThrow(ModelVersionRestoreServiceImpl::notFound);
        ModelVersion latest = versionRepository
                .findByModelIdOrderByVersionNumberDesc(modelId, PageRequest.of(0, 1))
                .stream().findFirst().orElseThrow(ModelVersionRestoreServiceImpl::notFound);
        if (!latest.getModelVersionId().equals(request.baseVersionId())) {
            throw new ApiException(HttpStatus.CONFLICT, "The base Model Version is stale.");
        }

        List<ModelFile> historicalFiles = fileRepository
                .findByModelVersionIdOrderByFileIdAsc(versionId);
        if (historicalFiles.isEmpty() || historicalFiles.stream().filter(ModelFile::isPrimary).count() != 1
                || historicalFiles.stream().anyMatch(file -> file.isPrimary()
                        && file.getFileRole() == ModelFileRole.PREVIEW)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "The selected Model Version has no usable primary file.");
        }
        var validator = new ModelFileValidator();
        for (ModelFile file : historicalFiles) {
            if (file.getFileId() == null || file.getFileRole() == null
                    || file.getStorageKey() == null || file.getStorageKey().isBlank()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "The selected Model Version has invalid file metadata.");
            }
            validator.validateMetadata(filename(file), file.getMimeType(), file.getFileRole());
        }

        List<String> createdKeys = new ArrayList<>();
        registerRollbackCleanup(createdKeys);
        String note = request.changeNote() == null
                ? "Restored from version " + source.getVersionNumber() : request.changeNote();
        ModelVersion restored = versionRepository.saveAndFlush(new ModelVersion(
                modelId, source.getModelVersionId(), null, authenticatedUserId,
                Math.addExact(latest.getVersionNumber(), 1), ModelSourceType.EDITOR_SAVE, note));
        Integer restoredId = Objects.requireNonNull(restored.getModelVersionId(),
                "Restored Model Version ID was not generated.");

        List<RestoredModelFileResponse> responses = new ArrayList<>();
        for (ModelFile historical : historicalFiles) {
            StoredModelFile copied;
            try (InputStream input = storageService.open(historical.getStorageKey())) {
                copied = storageService.store(input, filename(historical), historical.getMimeType(),
                        historical.getFileRole(), modelId, restoredId);
                createdKeys.add(copied.storageKey());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Historical Model file could not be read.", exception);
            }
            if ((historical.getFileSize() != null
                    && historical.getFileSize() != copied.fileSize())
                    || (historical.getFileHash() != null
                    && !historical.getFileHash().equalsIgnoreCase(copied.fileHash()))) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Historical Model file no longer matches its metadata.");
            }

            ModelFile saved = fileRepository.saveAndFlush(new ModelFile(restoredId,
                    historical.getFileRole(), historical.getFileFormat(), copied.storageKey(),
                    historical.getMimeType() == null ? copied.mimeType() : historical.getMimeType(),
                    copied.fileHash(), copied.fileSize(), historical.isPrimary()));
            Integer fileId = Objects.requireNonNull(saved.getFileId(),
                    "Restored Model file ID was not generated.");
            responses.add(new RestoredModelFileResponse(fileId, saved.getFileRole(),
                    saved.getFileFormat(), saved.getFileSize(), saved.isPrimary(),
                    "/api/models/" + modelId + "/files/" + fileId + "/download"));
        }
        return new RestoreModelVersionResponse(modelId, restoredId, restored.getVersionNumber(),
                restored.getParentVersionId(), restored.getSourceType(), restored.getChangeNote(),
                restored.getCreatedAt(), List.copyOf(responses));
    }

    private static String filename(ModelFile file) {
        return "model-" + file.getFileId() + "." + file.getFileFormat();
    }

    private void registerRollbackCleanup(List<String> createdKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    for (String key : createdKeys) {
                        try {
                            storageService.deleteForCleanup(key);
                        } catch (RuntimeException exception) {
                            log.error("Restored Model file cleanup failed; reconcile key={} errorType={}",
                                    key, exception.getClass().getSimpleName());
                        }
                    }
                } else if (status == STATUS_UNKNOWN && !createdKeys.isEmpty()) {
                    log.error("Model restore completion is unknown; reconcile {} new file objects",
                            createdKeys.size());
                }
            }
        });
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Model or version was not found.");
    }
}
