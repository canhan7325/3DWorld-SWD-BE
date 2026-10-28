package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.features.ai.dto.request.AiDimensionsRequest;
import com.project.w_3d_world.features.ai.dto.request.UpdateAiProjectRequest;
import com.project.w_3d_world.features.ai.dto.response.AiDimensionsResponse;
import com.project.w_3d_world.features.ai.dto.response.AiProjectDetailResponse;
import com.project.w_3d_world.features.ai.dto.response.AiProjectSummaryResponse;
import com.project.w_3d_world.features.ai.dto.response.AiVersionDetailResponse;

import java.util.List;

public interface AiProjectService {

    List<AiProjectSummaryResponse> listProjects(int userId);

    AiProjectDetailResponse getProject(int userId, int modelId);

    AiProjectDetailResponse updateProject(int userId, int modelId, UpdateAiProjectRequest request);

    AiVersionDetailResponse getVersion(int userId, int versionId);

    AiDimensionsResponse computeDimensions(int userId, int versionId, AiDimensionsRequest request);
}
