package com.project.w_3d_world.features.studio.service.impl;

import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.studio.dto.request.*;
import com.project.w_3d_world.features.studio.dto.response.*;
import com.project.w_3d_world.features.studio.service.StudioModelService;
import com.project.w_3d_world.features.studio.service.StudioProjectService;
import com.project.w_3d_world.features.studio.service.StudioSnapshotService;
import com.project.w_3d_world.features.studio.service.StudioVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * High-level Facade delegating to specialized Studio domain services:
 * - {@link StudioProjectService} for 3D Studio project lifecycle, metadata, thumbnails, and duplicate.
 * - {@link StudioVersionService} for scene graph revisioning, multi-version save, and file downloads.
 * - {@link StudioSnapshotService} for project snapshots, STL file uploads, and inspection metadata.
 */
@Slf4j
@Service
public class StudioModelServiceImpl implements StudioModelService {

    private final StudioProjectService studioProjectService;
    private final StudioVersionService studioVersionService;
    private final StudioSnapshotService studioSnapshotService;

    @Autowired
    public StudioModelServiceImpl(
            StudioProjectService studioProjectService,
            StudioVersionService studioVersionService,
            StudioSnapshotService studioSnapshotService
    ) {
        this.studioProjectService = studioProjectService;
        this.studioVersionService = studioVersionService;
        this.studioSnapshotService = studioSnapshotService;
    }

    /**
     * Backward-compatible 6-argument constructor for existing tests and direct instantiations.
     */
    public StudioModelServiceImpl(
            Model3DRepository model3DRepository,
            ModelVersionRepository modelVersionRepository,
            ModelFileRepository modelFileRepository,
            ModelStorageService storageService,
            ModelDownloadService downloadService,
            AuditLogRepository auditLogRepository
    ) {
        StudioProjectService projectSvc = new StudioProjectServiceImpl(
                model3DRepository, modelVersionRepository, modelFileRepository,
                storageService, downloadService, auditLogRepository
        );
        StudioVersionService versionSvc = new StudioVersionServiceImpl(
                model3DRepository, modelVersionRepository, modelFileRepository,
                storageService, downloadService, auditLogRepository, projectSvc
        );
        StudioSnapshotService snapshotSvc = new StudioSnapshotServiceImpl(
                model3DRepository, modelVersionRepository, modelFileRepository,
                storageService, auditLogRepository
        );

        this.studioProjectService = projectSvc;
        this.studioVersionService = versionSvc;
        this.studioSnapshotService = snapshotSvc;
    }

    // ---------------- StudioProjectService delegations ----------------

    @Override
    public List<StudioProjectSummaryResponse> listProjects(int userId) {
        return studioProjectService.listProjects(userId);
    }

    @Override
    public StudioProjectDetailResponse createProject(int userId, CreateStudioProjectRequest request) {
        return studioProjectService.createProject(userId, request);
    }

    @Override
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

                // Versions are ordered newest first: use the newest version that has a scene.
                // Snapshot versions only hold an STL file, so "latest" alone would load an empty scene.
                if (sceneData == null && f.getFileRole() == ModelFileRole.EDITABLE_SCENE) {
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
        return doSaveScene(userId, modelId, request.expectedVersion(), request.scene(), request.note(), null);
    }

    @Transactional
    public StudioProjectDetailResponse saveProjectContent(int userId, int modelId, SaveStudioContentRequest request) {
        if (request == null || request.baseRevision() == null || request.baseRevision() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "baseRevision must be at least 1.");
        }
        if (request.name() != null && !request.name().isBlank()) {
            Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));
            model.updateMetadata(request.name().trim(), null, null);
            model3DRepository.saveAndFlush(model);
        }
        Object scene = request.scene();
        if (scene == null && request.nodes() != null) {
            scene = Map.of("nodes", request.nodes());
        }
        return doSaveScene(userId, modelId, request.baseRevision(), scene, request.note(), request.thumbnail());
    }

    @Override
    public StudioProjectDetailResponse updateProject(int userId, int modelId, UpdateStudioProjectRequest request) {
        return studioProjectService.updateProject(userId, modelId, request);
    }

    @Override
    public StudioProjectDetailResponse duplicateProject(int userId, int modelId) {
        return studioProjectService.duplicateProject(userId, modelId);
    }

    @Override
    public void deleteProject(int userId, int modelId) {
        studioProjectService.deleteProject(userId, modelId);
    }

    @Override
    public AuthorizedModelDownload downloadThumbnail(int userId, int modelId) {
        return studioProjectService.downloadThumbnail(userId, modelId);
    }

    // ---------------- StudioVersionService delegations ----------------

    @Override
    public StudioProjectDetailResponse saveProject(int userId, int modelId, SaveStudioProjectRequest request) {
        return studioVersionService.saveProject(userId, modelId, request);
    }

    @Override
    public StudioProjectDetailResponse saveProjectContent(int userId, int modelId, SaveStudioContentRequest request) {
        return studioVersionService.saveProjectContent(userId, modelId, request);
    }

    @Transactional
    public StudioSnapshotResponse createSnapshotWithFile(int userId, int modelId, CreateStudioSnapshotRequest meta, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Snapshot STL file is required.");
        }
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project has no base version."));

        int nextVer = latest.getVersionNumber() + 1;
        String note = (meta != null && meta.note() != null && !meta.note().isBlank())
                ? meta.note().trim()
                : "Snapshot export v" + nextVer;

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read uploaded snapshot file.");
        }

        if (fileBytes.length < 15) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid STL file: content too small.");
        }

        String computedHash;
        try {
            computedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fileBytes));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash snapshot file: " + e.getMessage());
        }

        ModelVersion snapVersion = modelVersionRepository.saveAndFlush(new ModelVersion(
                modelId,
                latest.getModelVersionId(),
                null,
                userId,
                nextVer,
                ModelSourceType.EDITOR_SAVE,
                note
        ));

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            stored = storageService.store(
                    in,
                    "snapshot-v" + nextVer + ".stl",
                    "model/stl",
                    ModelFileRole.SOURCE_MODEL,
                    modelId,
                    snapVersion.getModelVersionId()
            );
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store snapshot STL file: " + e.getMessage());
        }

        ModelFile snapFile = modelFileRepository.saveAndFlush(new ModelFile(
                snapVersion.getModelVersionId(),
                ModelFileRole.SOURCE_MODEL,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_SNAPSHOT_CREATED", "MODEL",
                modelId, "Created STL snapshot " + snapVersion.getVersionNumber() + " (" + note + ")", null
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
                downloadUrl,
                code,
                snapVersion.getVersionNumber(),
                computedHash,
                meta != null ? meta.dims() : null,
                meta != null && meta.parts() != null ? meta.parts() : Integer.valueOf(1),
                meta != null ? meta.triangles() : null,
                "READY",
                meta != null && meta.unit() != null ? meta.unit() : "mm",
                stored.fileSize(),
                meta != null && meta.acknowledgedWarnings() != null ? meta.acknowledgedWarnings() : List.of(),
                meta != null ? meta.consentVersion() : "2026-10"
        );
    }

    @Override
    public StudioProjectDetailResponse.StudioVersionSummary getVersion(int userId, int modelId, int versionNumber) {
        return studioVersionService.getVersion(userId, modelId, versionNumber);
    }

    @Override
    public AuthorizedModelDownload downloadFile(int userId, int modelId, int fileId) {
        return studioVersionService.downloadFile(userId, modelId, fileId);
    }

    // ---------------- StudioSnapshotService delegations ----------------

    @Override
    public List<StudioSnapshotResponse> listSnapshots(int userId, int modelId) {
        return studioSnapshotService.listSnapshots(userId, modelId);
    }

    @Override
    public StudioSnapshotResponse createSnapshot(int userId, int modelId, CreateStudioSnapshotRequest request) {
        return studioSnapshotService.createSnapshot(userId, modelId, request);
    }

    @Override
    public StudioSnapshotResponse createSnapshotWithFile(int userId, int modelId, CreateStudioSnapshotRequest meta, MultipartFile file) {
        return studioSnapshotService.createSnapshotWithFile(userId, modelId, meta, file);
    }

    @Override
    public StudioSnapshotResponse updateSnapshot(int userId, int modelId, String snapshotCode, UpdateStudioSnapshotRequest request) {
        return studioSnapshotService.updateSnapshot(userId, modelId, snapshotCode, request);
    }
}
