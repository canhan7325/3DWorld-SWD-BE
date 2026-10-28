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
        return studioProjectService.getProject(userId, modelId);
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

    @Override
    public List<StudioProjectDetailResponse.StudioVersionSummary> getVersions(int userId, int modelId) {
        return studioVersionService.getVersions(userId, modelId);
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
