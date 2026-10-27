package com.project.w_3d_world.features.studio.service;

import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.studio.dto.request.*;
import com.project.w_3d_world.features.studio.dto.response.*;

import java.util.List;

/**
 * Service contract for 3D Studio project lifecycle, version history, scene persistence, and snapshots.
 */
public interface StudioModelService {

    List<StudioProjectSummaryResponse> listProjects(int userId);

    StudioProjectDetailResponse createProject(int userId, CreateStudioProjectRequest request);

    StudioProjectDetailResponse getProject(int userId, int modelId);

    StudioProjectDetailResponse saveProject(int userId, int modelId, SaveStudioProjectRequest request);

    StudioProjectDetailResponse saveProjectContent(int userId, int modelId, SaveStudioContentRequest request);

    StudioProjectDetailResponse updateProject(int userId, int modelId, UpdateStudioProjectRequest request);

    StudioProjectDetailResponse duplicateProject(int userId, int modelId);

    void deleteProject(int userId, int modelId);

    AuthorizedModelDownload downloadThumbnail(int userId, int modelId);

    List<StudioSnapshotResponse> listSnapshots(int userId, int modelId);

    StudioSnapshotResponse createSnapshot(int userId, int modelId, CreateStudioSnapshotRequest request);

    StudioSnapshotResponse updateSnapshot(int userId, int modelId, String snapshotCode, UpdateStudioSnapshotRequest request);

    List<StudioProjectDetailResponse.StudioVersionSummary> getVersions(int userId, int modelId);

    StudioProjectDetailResponse.StudioVersionSummary getVersion(int userId, int modelId, int versionNumber);

    AuthorizedModelDownload downloadFile(int userId, int modelId, int fileId);
}
