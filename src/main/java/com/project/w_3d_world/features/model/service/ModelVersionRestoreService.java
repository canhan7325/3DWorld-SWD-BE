package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.RestoreModelVersionResponse;

public interface ModelVersionRestoreService {
    RestoreModelVersionResponse restoreVersion(
            Integer authenticatedUserId, Integer modelId, Integer versionId,
            RestoreModelVersionRequest request);
}
