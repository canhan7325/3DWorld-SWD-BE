package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.SaveModelVersionResponse;
import com.project.w_3d_world.features.model.dto.response.SavedModelFileResponse;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.service.ModelVersionSaveService;
import com.project.w_3d_world.features.model.storage.ModelStorageException;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelVersionSaveServiceImpl implements ModelVersionSaveService {
    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;
    private final ModelStorageService storageService;

    @Override
    @Transactional
    public SaveModelVersionResponse saveVersion(
            Integer authenticatedUserId, Integer modelId,
            SaveModelVersionRequest request, MultipartFile file) {
        ModelServiceValidation.validateSaveRequest(authenticatedUserId, modelId, request, file);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Model save requires an active database transaction.");
        }

        modelRepository.findOwnedActiveByIdForUpdate(modelId, authenticatedUserId)
                .orElseThrow(ModelVersionSaveServiceImpl::notFound);
        ModelVersion base = versionRepository
                .findByModelVersionIdAndModelId(request.baseVersionId(), modelId)
                .orElseThrow(ModelVersionSaveServiceImpl::notFound);
        ModelVersion latest = versionRepository
                .findByModelIdOrderByVersionNumberDesc(modelId, PageRequest.of(0, 1))
                .stream().findFirst().orElseThrow(ModelVersionSaveServiceImpl::notFound);
        if (!base.getModelVersionId().equals(latest.getModelVersionId())) {
            throw new ApiException(HttpStatus.CONFLICT, "The base Model Version is stale.");
        }

        String[] storedKey = {null};
        registerRollbackCleanup(storedKey);
        ModelVersion savedVersion = versionRepository.saveAndFlush(new ModelVersion(
                modelId, base.getModelVersionId(), null, authenticatedUserId,
                Math.addExact(latest.getVersionNumber(), 1), ModelSourceType.EDITOR_SAVE,
                request.changeNote()));
        Integer versionId = Objects.requireNonNull(savedVersion.getModelVersionId(),
                "Model version ID was not generated.");

        StoredModelFile stored;
        try (InputStream input = file.getInputStream()) {
            stored = storageService.store(input, file.getOriginalFilename(), file.getContentType(),
                    ModelFileRole.SOURCE_MODEL, modelId, versionId);
            storedKey[0] = stored.storageKey();
        } catch (IOException exception) {
            throw new ModelStorageException(ModelStorageException.Code.IO_FAILURE,
                    "Saved model file could not be read.", exception);
        }

        ModelFile savedFile = fileRepository.saveAndFlush(new ModelFile(versionId,
                ModelFileRole.SOURCE_MODEL, stored.fileFormat(), stored.storageKey(),
                stored.mimeType(), stored.fileHash(), stored.fileSize(), true));
        Integer fileId = Objects.requireNonNull(savedFile.getFileId(),
                "Model file ID was not generated.");
        String downloadPath = "/api/models/" + modelId + "/files/" + fileId + "/download";
        String fileName = "model-" + modelId + "-file-" + fileId + "." + stored.fileFormat();
        return new SaveModelVersionResponse(modelId, versionId, savedVersion.getVersionNumber(),
                savedVersion.getParentVersionId(), savedVersion.getSourceType(),
                savedVersion.getChangeNote(), savedVersion.getCreatedAt(),
                new SavedModelFileResponse(fileId, fileName, stored.fileFormat(),
                        stored.fileSize(), downloadPath));
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
                        log.error("Saved model file cleanup failed; reconcile key={} errorType={}",
                                storageKey, exception.getClass().getSimpleName());
                    }
                } else if (status == STATUS_UNKNOWN) {
                    log.error("Model save completion is unknown; retain and reconcile key={}", storageKey);
                }
            }
        });
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Model or version was not found.");
    }
}
