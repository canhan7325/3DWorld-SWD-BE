package com.project.w_3d_world.features.studio.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.studio.dto.request.SaveStudioContentRequest;
import com.project.w_3d_world.features.studio.dto.request.SaveStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;
import com.project.w_3d_world.features.studio.service.StudioProjectService;
import com.project.w_3d_world.features.studio.service.StudioVersionService;
import com.project.w_3d_world.features.studio.util.StudioSceneHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudioVersionServiceImpl implements StudioVersionService {

    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final ModelStorageService storageService;
    private final ModelDownloadService downloadService;
    private final AuditLogRepository auditLogRepository;
    private final StudioProjectService studioProjectService;

    @Override
    @Transactional
    public StudioProjectDetailResponse saveProject(int userId, int modelId, SaveStudioProjectRequest request) {
        if (request == null || request.expectedVersion() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "expectedVersion must be at least 1.");
        }
        return doSaveScene(userId, modelId, request.expectedVersion(), request.scene(), request.note(), null);
    }

    @Override
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

    private StudioProjectDetailResponse doSaveScene(int userId, int modelId, int baseRevision, Object scene, String note, String thumbnailData) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project has no base version."));

        if (latest.getVersionNumber() != baseRevision) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "REVISION_CONFLICT: Expected version " + baseRevision
                            + " does not match current server version " + latest.getVersionNumber() + ".");
        }

        byte[] sceneBytes = StudioSceneHelper.serializeScene(scene);
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

        studioProjectService.saveThumbnailIfPresent(userId, modelId, newVersion.getModelVersionId(), thumbnailData);

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_VERSION_SAVED", "MODEL",
                modelId, "Saved v" + nextVersionNumber + " (" + changeNote + ")", null
        ));

        return studioProjectService.getProject(userId, modelId);
    }

    @Override
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

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadFile(int userId, int modelId, int fileId) {
        return downloadService.openAuthorizedDownload(userId, modelId, fileId);
    }
}
