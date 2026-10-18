package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.dto.response.ModelDetailResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelResponse;

public interface ModelLibraryService {
    PagedModelResponse getMyModels(Integer authenticatedUserId, int page, int size);

    ModelDetailResponse getModelDetails(Integer authenticatedUserId, Integer modelId);
}
