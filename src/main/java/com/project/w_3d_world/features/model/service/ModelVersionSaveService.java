package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.SaveModelVersionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ModelVersionSaveService {
    SaveModelVersionResponse saveVersion(
            Integer authenticatedUserId, Integer modelId,
            SaveModelVersionRequest request, MultipartFile file);
}
