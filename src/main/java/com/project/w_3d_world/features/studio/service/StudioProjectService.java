package com.project.w_3d_world.features.studio.service;

import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.studio.dto.request.CreateStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.request.UpdateStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectSummaryResponse;

import java.util.List;

public interface StudioProjectService {

    List<StudioProjectSummaryResponse> listProjects(int userId);

    StudioProjectDetailResponse createProject(int userId, CreateStudioProjectRequest request);

    StudioProjectDetailResponse getProject(int userId, int modelId);

    StudioProjectDetailResponse updateProject(int userId, int modelId, UpdateStudioProjectRequest request);

    StudioProjectDetailResponse duplicateProject(int userId, int modelId);

    void deleteProject(int userId, int modelId);

    AuthorizedModelDownload downloadThumbnail(int userId, int modelId);

    void saveThumbnailIfPresent(int userId, int modelId, int versionId, String thumbnailData);
}
