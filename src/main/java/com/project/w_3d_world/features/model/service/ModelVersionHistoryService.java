package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.dto.response.ModelVersionDetailResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelVersionsResponse;

public interface ModelVersionHistoryService {
    PagedModelVersionsResponse getModelVersions(
            Integer authenticatedUserId, Integer modelId, int page, int size);

    ModelVersionDetailResponse getModelVersionDetails(
            Integer authenticatedUserId, Integer modelId, Integer versionId);
}
