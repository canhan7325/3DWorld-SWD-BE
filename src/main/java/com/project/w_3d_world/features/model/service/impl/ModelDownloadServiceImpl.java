package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.ModelStorageException;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModelDownloadServiceImpl implements ModelDownloadService {
    private static final Map<String, String> MIME_TYPES = Map.of(
            "glb", "model/gltf-binary", "stl", "model/stl", "obj", "model/obj", "3mf", "model/3mf",
            "json", "application/json", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg");
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("glb", "glb"), Map.entry("stl", "stl"), Map.entry("obj", "obj"),
            Map.entry("3mf", "3mf"), Map.entry("json", "json"), Map.entry("png", "png"),
            Map.entry("jpg", "jpg"), Map.entry("jpeg", "jpeg"));

    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;
    private final ModelStorageService storageService;

    @Override
    public AuthorizedModelDownload openAuthorizedDownload(
            Integer authenticatedUserId, Integer modelId, Integer fileId) {
        ModelServiceValidation.validatePositive(authenticatedUserId, "Authenticated user ID");
        ModelServiceValidation.validatePositive(modelId, "Model ID");
        ModelServiceValidation.validatePositive(fileId, "File ID");
        modelRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, authenticatedUserId)
                .orElseThrow(ModelDownloadServiceImpl::notFound);

        ModelFile file = fileRepository.findById(fileId).orElseThrow(ModelDownloadServiceImpl::notFound);
        versionRepository.findByModelVersionIdAndModelId(file.getModelVersionId(), modelId)
                .orElseThrow(ModelDownloadServiceImpl::notFound);

        String extension = ModelServiceValidation.normalizeFileFormat(file.getFileFormat());
        String safeExtension = EXTENSIONS.getOrDefault(extension, "bin");
        String contentType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        String filename = "model-" + modelId + "-file-" + fileId + "." + safeExtension;
        InputStream input;
        try {
            input = storageService.open(file.getStorageKey());
        } catch (ModelStorageException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The model file is temporarily unavailable.");
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The model file is temporarily unavailable.");
        }
        try {
            return new AuthorizedModelDownload(input, filename, contentType, file.getFileSize());
        } catch (RuntimeException exception) {
            try { input.close(); } catch (Exception ignored) { }
            throw exception;
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Model file was not found.");
    }
}
