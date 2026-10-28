package com.project.w_3d_world.features.studio.service;

import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.studio.dto.request.SaveStudioContentRequest;
import com.project.w_3d_world.features.studio.dto.request.SaveStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;

import java.util.List;

public interface StudioVersionService {

    StudioProjectDetailResponse saveProject(int userId, int modelId, SaveStudioProjectRequest request);

    StudioProjectDetailResponse saveProjectContent(int userId, int modelId, SaveStudioContentRequest request);

    List<StudioProjectDetailResponse.StudioVersionSummary> getVersions(int userId, int modelId);

    StudioProjectDetailResponse.StudioVersionSummary getVersion(int userId, int modelId, int versionNumber);

    AuthorizedModelDownload downloadFile(int userId, int modelId, int fileId);
}
