package com.project.w_3d_world.features.studio.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
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
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.studio.dto.request.CreateStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.request.UpdateStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectSummaryResponse;
import com.project.w_3d_world.features.studio.service.StudioProjectService;
import com.project.w_3d_world.features.studio.util.StudioSceneHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudioProjectServiceImpl implements StudioProjectService {

    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final ModelStorageService storageService;
    private final ModelDownloadService downloadService;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StudioProjectSummaryResponse> listProjects(int userId) {
        List<Model3D> models = model3DRepository.findAll().stream()
                .filter(m -> Objects.equals(m.getOwnerUserId(), userId)
                        && m.getDeletedAt() == null
                        && (m.getModelType() == ModelType.WEB_EDITOR || m.getModelType() == ModelType.AI_GENERATOR))
                .sorted(Comparator.comparing(Model3D::getCreatedAt).reversed())
                .toList();

        List<StudioProjectSummaryResponse> result = new ArrayList<>();
        for (Model3D m : models) {
            int latestVersion = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(m.getModelId())
                    .map(ModelVersion::getVersionNumber)
                    .orElse(1);

            result.add(new StudioProjectSummaryResponse(
                    m.getModelId(),
                    m.getModelName(),
                    m.getModelType().name(),
                    m.getOriginType().name(),
                    latestVersion,
                    m.getCreatedAt(),
                    m.getUpdatedAt()
            ));
        }
        return result;
    }

    @Override
    @Transactional
    public StudioProjectDetailResponse createProject(int userId, CreateStudioProjectRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name is required.");
        }
        String name = request.name().trim();
        if (name.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name cannot exceed 255 characters.");
        }

        byte[] sceneBytes = StudioSceneHelper.serializeScene(request.scene());

        Model3D model = model3DRepository.saveAndFlush(new Model3D(
                userId,
                name,
                "Studio project: " + name,
                ModelType.WEB_EDITOR,
                ModelOriginType.EDITOR,
                "PRIVATE"
        ));

        ModelVersion version = modelVersionRepository.saveAndFlush(new ModelVersion(
                model.getModelId(),
                null,
                null,
                userId,
                1,
                ModelSourceType.EDITOR_SAVE,
                "Initial Studio scene"
        ));

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(sceneBytes)) {
            stored = storageService.store(
                    in,
                    "scene.json",
                    "application/json",
                    ModelFileRole.EDITABLE_SCENE,
                    model.getModelId(),
                    version.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store scene data: " + e.getMessage());
        }

        modelFileRepository.saveAndFlush(new ModelFile(
                version.getModelVersionId(),
                ModelFileRole.EDITABLE_SCENE,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        saveThumbnailIfPresent(userId, model.getModelId(), version.getModelVersionId(), request.thumbnail());

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_PROJECT_CREATED", "MODEL",
                model.getModelId(), "Created Studio project " + name, null
        ));

        return getProject(userId, model.getModelId());
    }

    @Override
    @Transactional(readOnly = true)
    public StudioProjectDetailResponse getProject(int userId, int modelId) {
        Model3D model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        if (versions.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No versions found for project.");
        }

        ModelVersion latest = versions.get(0);
        Object sceneData = null;

        List<StudioProjectDetailResponse.StudioVersionSummary> versionSummaries = new ArrayList<>();
        for (ModelVersion v : versions) {
            List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v.getModelVersionId());
            List<StudioProjectDetailResponse.StudioFileInfo> fileInfos = new ArrayList<>();

            for (ModelFile f : files) {
                fileInfos.add(new StudioProjectDetailResponse.StudioFileInfo(
                        f.getFileId(),
                        f.getFileRole().name(),
                        f.getFileFormat(),
                        f.getFileSize(),
                        "/api/studio/projects/" + modelId + "/files/" + f.getFileId() + "/download"
                ));

                if (sceneData == null && v.getModelVersionId().equals(latest.getModelVersionId())
                        && f.getFileRole() == ModelFileRole.EDITABLE_SCENE) {
                    try (InputStream stream = storageService.open(f.getStorageKey())) {
                        sceneData = StudioSceneHelper.parseScene(stream);
                    } catch (Exception e) {
                        log.warn("Failed to load scene file for model {}: {}", modelId, e.getMessage());
                    }
                }
            }

            versionSummaries.add(new StudioProjectDetailResponse.StudioVersionSummary(
                    v.getModelVersionId(),
                    v.getParentVersionId(),
                    v.getVersionNumber(),
                    v.getSourceType().name(),
                    v.getChangeNote(),
                    v.getCreatedAt(),
                    fileInfos
            ));
        }

        if (sceneData == null) {
            ModelFile glb = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(latest.getModelVersionId())
                    .stream()
                    .filter(f -> f.getFileRole() == ModelFileRole.VIEWABLE_MODEL)
                    .findFirst()
                    .orElse(null);

            if (glb != null) {
                sceneData = Map.of(
                        "name", model.getModelName(),
                        "sourceGlbFileId", glb.getFileId(),
                        "nodes", List.of(Map.of(
                                "name", model.getModelName(),
                                "type", "mesh",
                                "sourceFileId", glb.getFileId()
                        ))
                );
            } else {
                sceneData = Map.of("nodes", List.of());
            }
        }

        return new StudioProjectDetailResponse(
                model.getModelId(),
                model.getModelName(),
                model.getModelType().name(),
                model.getOriginType().name(),
                latest.getVersionNumber(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                sceneData,
                versionSummaries
        );
    }

    @Override
    @Transactional
    public StudioProjectDetailResponse updateProject(int userId, int modelId, UpdateStudioProjectRequest request) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        if (request != null) {
            model.updateMetadata(request.name(), request.description(), null);
            model3DRepository.saveAndFlush(model);
            auditLogRepository.save(new AuditLog(
                    userId, "STUDIO_PROJECT_UPDATED", "MODEL",
                    modelId, "Updated metadata for Studio project " + model.getModelName(), null
            ));
        }

        return getProject(userId, modelId);
    }

    @Override
    @Transactional
    public StudioProjectDetailResponse duplicateProject(int userId, int modelId) {
        Model3D original = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Original project has no versions."));

        byte[] sceneBytes = null;
        List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(latest.getModelVersionId());
        for (ModelFile f : files) {
            if (f.getFileRole() == ModelFileRole.EDITABLE_SCENE) {
                try (InputStream in = storageService.open(f.getStorageKey())) {
                    sceneBytes = in.readAllBytes();
                    break;
                } catch (Exception e) {
                    log.warn("Failed to read original scene for duplicate: {}", e.getMessage());
                }
            }
        }

        if (sceneBytes == null) {
            sceneBytes = StudioSceneHelper.serializeScene(Map.of("nodes", List.of()));
        }

        String newName = "Copy of " + original.getModelName();
        if (newName.length() > 255) {
            newName = newName.substring(0, 255);
        }

        Model3D newModel = model3DRepository.saveAndFlush(new Model3D(
                userId,
                newName,
                "Duplicated from project #" + modelId,
                ModelType.WEB_EDITOR,
                ModelOriginType.EDITOR,
                "PRIVATE"
        ));

        ModelVersion newVersion = modelVersionRepository.saveAndFlush(new ModelVersion(
                newModel.getModelId(),
                null,
                null,
                userId,
                1,
                ModelSourceType.EDITOR_SAVE,
                "Duplicated initial revision"
        ));

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(sceneBytes)) {
            stored = storageService.store(
                    in,
                    "scene.json",
                    "application/json",
                    ModelFileRole.EDITABLE_SCENE,
                    newModel.getModelId(),
                    newVersion.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy scene for duplicate: " + e.getMessage());
        }

        modelFileRepository.saveAndFlush(new ModelFile(
                newVersion.getModelVersionId(),
                ModelFileRole.EDITABLE_SCENE,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_PROJECT_DUPLICATED", "MODEL",
                newModel.getModelId(), "Duplicated from project #" + modelId, null
        ));

        return getProject(userId, newModel.getModelId());
    }

    @Override
    @Transactional
    public void deleteProject(int userId, int modelId) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        model.markDeleted();
        model3DRepository.saveAndFlush(model);

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_PROJECT_DELETED", "MODEL",
                modelId, "Soft-deleted Studio project " + model.getModelName(), null
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadThumbnail(int userId, int modelId) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        for (ModelVersion v : versions) {
            List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v.getModelVersionId());
            for (ModelFile f : files) {
                if (f.getFileRole() == ModelFileRole.PREVIEW) {
                    return downloadService.openAuthorizedDownload(userId, modelId, f.getFileId());
                }
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "No thumbnail image available for this project.");
    }

    @Override
    public void saveThumbnailIfPresent(int userId, int modelId, int versionId, String thumbnailData) {
        if (thumbnailData == null || thumbnailData.isBlank()) {
            return;
        }
        try {
            String base64Content = thumbnailData;
            String format = "png";
            String mime = "image/png";
            if (thumbnailData.startsWith("data:")) {
                int commaIdx = thumbnailData.indexOf(',');
                if (commaIdx > 0) {
                    String prefix = thumbnailData.substring(5, commaIdx);
                    if (prefix.contains("image/jpeg") || prefix.contains("image/jpg")) {
                        format = "jpg";
                        mime = "image/jpeg";
                    } else if (prefix.contains("image/png")) {
                        format = "png";
                        mime = "image/png";
                    }
                    base64Content = thumbnailData.substring(commaIdx + 1);
                }
            }
            byte[] imageBytes = Base64.getDecoder().decode(base64Content.trim());
            if (imageBytes.length > 2_000_000) {
                log.warn("Thumbnail image exceeds 2MB limit: {} bytes", imageBytes.length);
                return;
            }
            StoredModelFile stored = storageService.store(
                    new ByteArrayInputStream(imageBytes),
                    "preview." + format,
                    mime,
                    ModelFileRole.PREVIEW,
                    modelId,
                    versionId
            );
            modelFileRepository.saveAndFlush(new ModelFile(
                    versionId,
                    ModelFileRole.PREVIEW,
                    stored.fileFormat(),
                    stored.storageKey(),
                    stored.mimeType(),
                    stored.fileHash(),
                    stored.fileSize(),
                    false
            ));
        } catch (Exception e) {
            log.warn("Failed to persist thumbnail preview: {}", e.getMessage());
        }
    }
}
