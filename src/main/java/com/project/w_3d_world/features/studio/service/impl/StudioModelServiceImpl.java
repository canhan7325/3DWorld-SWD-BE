package com.project.w_3d_world.features.studio.service.impl;

import com.project.w_3d_world.features.studio.dto.request.*;
import com.project.w_3d_world.features.studio.dto.response.*;
import com.project.w_3d_world.features.studio.service.StudioModelService;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudioModelServiceImpl implements StudioModelService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_SCENE_SIZE = 5_000_000; // 5 MB

    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final ModelStorageService storageService;
    private final ModelDownloadService downloadService;
    private final AuditLogRepository auditLogRepository;

    private byte[] serializeScene(Object scene) {
        if (scene == null) {
            return "{\"nodes\":[]}".getBytes(StandardCharsets.UTF_8);
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(scene);
            if (bytes.length > MAX_SCENE_SIZE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Scene graph exceeds maximum size limit of 5 MB.");
            }
            return bytes;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid scene JSON: " + e.getMessage());
        }
    }

    private Object parseScene(InputStream input) {
        if (input == null) return Map.of("nodes", List.of());
        try {
            return MAPPER.readTree(input);
        } catch (Exception e) {
            return Map.of("nodes", List.of());
        }
    }

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

    @Transactional
    public StudioProjectDetailResponse createProject(int userId, CreateStudioProjectRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name is required.");
        }
        String name = request.name().trim();
        if (name.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Project name cannot exceed 255 characters.");
        }

        byte[] sceneBytes = serializeScene(request.scene());

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

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_PROJECT_CREATED", "MODEL",
                model.getModelId(), "Created Studio project " + name, null
        ));

        return getProject(userId, model.getModelId());
    }

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
                        sceneData = parseScene(stream);
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

    @Transactional
    public StudioProjectDetailResponse saveProject(int userId, int modelId, SaveStudioProjectRequest request) {
        if (request == null || request.expectedVersion() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "expectedVersion must be at least 1.");
        }
        return doSaveScene(userId, modelId, request.expectedVersion(), request.scene(), request.note());
    }

    @Transactional
    public StudioProjectDetailResponse saveProjectContent(int userId, int modelId, SaveStudioContentRequest request) {
        if (request == null || request.baseRevision() == null || request.baseRevision() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "baseRevision must be at least 1.");
        }
        return doSaveScene(userId, modelId, request.baseRevision(), request.scene(), request.note());
    }

    private StudioProjectDetailResponse doSaveScene(int userId, int modelId, int baseRevision, Object scene, String note) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project has no base version."));

        if (latest.getVersionNumber() != baseRevision) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "REVISION_CONFLICT: Expected version " + baseRevision
                            + " does not match current server version " + latest.getVersionNumber() + ".");
        }

        byte[] sceneBytes = serializeScene(scene);
        int nextVersionNumber = latest.getVersionNumber() + 1;
        String changeNote = (note == null || note.isBlank())
                ? "Studio revision " + nextVersionNumber
                : note.trim();

        ModelVersion newVersion = modelVersionRepository.saveAndFlush(new ModelVersion(
                modelId,
                latest.getModelVersionId(),
                null,
                userId,
                nextVersionNumber,
                ModelSourceType.EDITOR_SAVE,
                changeNote
        ));

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(sceneBytes)) {
            stored = storageService.store(
                    in,
                    "scene.json",
                    "application/json",
                    ModelFileRole.EDITABLE_SCENE,
                    modelId,
                    newVersion.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store scene data: " + e.getMessage());
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

        model.updateMetadata(null, null, null);
        model3DRepository.saveAndFlush(model);

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_VERSION_SAVED", "MODEL",
                modelId, "Saved v" + nextVersionNumber + " (" + changeNote + ")", null
        ));

        return getProject(userId, modelId);
    }

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
            sceneBytes = serializeScene(Map.of("nodes", List.of()));
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

    @Transactional(readOnly = true)
    public List<StudioSnapshotResponse> listSnapshots(int userId, int modelId) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        List<StudioSnapshotResponse> result = new ArrayList<>();

        for (ModelVersion v : versions) {
            List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v.getModelVersionId());
            ModelFile targetFile = files.stream()
                    .filter(f -> f.getFileRole() == ModelFileRole.VIEWABLE_MODEL || f.getFileRole() == ModelFileRole.EDITABLE_SCENE)
                    .findFirst()
                    .orElse(null);

            String code = "SNAP-" + modelId + "-v" + v.getVersionNumber();
            String format = targetFile != null ? targetFile.getFileFormat() : "JSON";
            Long size = targetFile != null ? targetFile.getFileSize() : 0L;
            String downloadUrl = targetFile != null ? "/api/studio/projects/" + modelId + "/files/" + targetFile.getFileId() + "/download" : null;

            result.add(new StudioSnapshotResponse(
                    code,
                    modelId,
                    v.getVersionNumber(),
                    v.getChangeNote(),
                    format,
                    size,
                    v.getCreatedAt(),
                    downloadUrl
            ));
        }

        return result;
    }

    @Transactional
    public StudioSnapshotResponse createSnapshot(int userId, int modelId, CreateStudioSnapshotRequest request) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project has no base version."));

        int nextVer = latest.getVersionNumber() + 1;
        String note = (request != null && request.note() != null && !request.note().isBlank())
                ? request.note().trim()
                : "Snapshot export v" + nextVer;

        ModelVersion snapVersion = modelVersionRepository.saveAndFlush(new ModelVersion(
                modelId,
                latest.getModelVersionId(),
                null,
                userId,
                nextVer,
                ModelSourceType.EDITOR_SAVE,
                note
        ));

        // Copy scene or primary asset from latest
        byte[] assetBytes = null;
        String format = "json";
        String mime = "application/json";
        List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(latest.getModelVersionId());
        for (ModelFile f : files) {
            if (f.getFileRole() == ModelFileRole.EDITABLE_SCENE || f.getFileRole() == ModelFileRole.VIEWABLE_MODEL) {
                try (InputStream stream = storageService.open(f.getStorageKey())) {
                    assetBytes = stream.readAllBytes();
                    format = f.getFileFormat();
                    mime = f.getMimeType();
                    break;
                } catch (Exception ignored) {}
            }
        }

        if (assetBytes == null) {
            assetBytes = serializeScene(Map.of("nodes", List.of()));
        }

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(assetBytes)) {
            stored = storageService.store(
                    in,
                    "snapshot." + format.toLowerCase(Locale.ROOT),
                    mime,
                    ModelFileRole.VIEWABLE_MODEL,
                    modelId,
                    snapVersion.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store snapshot asset: " + e.getMessage());
        }

        ModelFile snapFile = modelFileRepository.saveAndFlush(new ModelFile(
                snapVersion.getModelVersionId(),
                ModelFileRole.VIEWABLE_MODEL,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_SNAPSHOT_CREATED", "MODEL",
                modelId, "Created snapshot " + snapVersion.getVersionNumber() + " (" + note + ")", null
        ));

        String code = "SNAP-" + modelId + "-v" + snapVersion.getVersionNumber();
        String downloadUrl = "/api/studio/projects/" + modelId + "/files/" + snapFile.getFileId() + "/download";

        return new StudioSnapshotResponse(
                code,
                modelId,
                snapVersion.getVersionNumber(),
                note,
                stored.fileFormat(),
                stored.fileSize(),
                snapVersion.getCreatedAt(),
                downloadUrl
        );
    }

    @Transactional
    public StudioSnapshotResponse updateSnapshot(int userId, int modelId, String snapshotCode, UpdateStudioSnapshotRequest request) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        if (snapshotCode == null || !snapshotCode.startsWith("SNAP-" + modelId + "-v")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid snapshot code for this project.");
        }

        int versionNumber;
        try {
            String verStr = snapshotCode.substring(snapshotCode.lastIndexOf("-v") + 2);
            versionNumber = Integer.parseInt(verStr);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot parse version number from snapshot code.");
        }

        ModelVersion version = modelVersionRepository.findByModelIdAndVersionNumber(modelId, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Snapshot version not found."));

        if (request != null && request.note() != null) {
            version.updateChangeNote(request.note().trim());
            modelVersionRepository.saveAndFlush(version);
        }

        ModelFile targetFile = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(version.getModelVersionId())
                .stream()
                .filter(f -> f.getFileRole() == ModelFileRole.VIEWABLE_MODEL || f.getFileRole() == ModelFileRole.EDITABLE_SCENE)
                .findFirst()
                .orElse(null);

        String format = targetFile != null ? targetFile.getFileFormat() : "JSON";
        Long size = targetFile != null ? targetFile.getFileSize() : 0L;
        String downloadUrl = targetFile != null ? "/api/studio/projects/" + modelId + "/files/" + targetFile.getFileId() + "/download" : null;

        return new StudioSnapshotResponse(
                snapshotCode,
                modelId,
                version.getVersionNumber(),
                version.getChangeNote(),
                format,
                size,
                version.getCreatedAt(),
                downloadUrl
        );
    }

    @Transactional(readOnly = true)
    public List<StudioProjectDetailResponse.StudioVersionSummary> getVersions(int userId, int modelId) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found."));

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        List<StudioProjectDetailResponse.StudioVersionSummary> result = new ArrayList<>();
        for (ModelVersion v : versions) {
            List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v.getModelVersionId());
            List<StudioProjectDetailResponse.StudioFileInfo> fileInfos = files.stream().map(f ->
                    new StudioProjectDetailResponse.StudioFileInfo(
                            f.getFileId(),
                            f.getFileRole().name(),
                            f.getFileFormat(),
                            f.getFileSize(),
                            "/api/studio/projects/" + modelId + "/files/" + f.getFileId() + "/download"
                    )
            ).toList();

            result.add(new StudioProjectDetailResponse.StudioVersionSummary(
                    v.getModelVersionId(),
                    v.getParentVersionId(),
                    v.getVersionNumber(),
                    v.getSourceType().name(),
                    v.getChangeNote(),
                    v.getCreatedAt(),
                    fileInfos
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public StudioProjectDetailResponse.StudioVersionSummary getVersion(int userId, int modelId, int versionNumber) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found."));

        ModelVersion v = modelVersionRepository.findByModelIdAndVersionNumber(modelId, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version " + versionNumber + " not found."));

        List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v.getModelVersionId());
        List<StudioProjectDetailResponse.StudioFileInfo> fileInfos = files.stream().map(f ->
                new StudioProjectDetailResponse.StudioFileInfo(
                        f.getFileId(),
                        f.getFileRole().name(),
                        f.getFileFormat(),
                        f.getFileSize(),
                        "/api/studio/projects/" + modelId + "/files/" + f.getFileId() + "/download"
                )
        ).toList();

        return new StudioProjectDetailResponse.StudioVersionSummary(
                v.getModelVersionId(),
                v.getParentVersionId(),
                v.getVersionNumber(),
                v.getSourceType().name(),
                v.getChangeNote(),
                v.getCreatedAt(),
                fileInfos
        );
    }

    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadFile(int userId, int modelId, int fileId) {
        return downloadService.openAuthorizedDownload(userId, modelId, fileId);
    }
}
