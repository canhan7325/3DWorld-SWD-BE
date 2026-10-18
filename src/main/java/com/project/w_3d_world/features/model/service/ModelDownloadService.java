package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;

public interface ModelDownloadService {
    AuthorizedModelDownload openAuthorizedDownload(
            Integer authenticatedUserId, Integer modelId, Integer fileId);
}
