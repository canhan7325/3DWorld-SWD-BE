package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.features.ai.dto.request.AiExportRequest;
import com.project.w_3d_world.features.ai.dto.response.AiExportResponse;
import com.project.w_3d_world.features.ai.provider.ProviderAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderTask;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelVersion;

public interface AiExportService {

    AiExportResponse exportVersion(int userId, int versionId, AiExportRequest request);

    AuthorizedModelDownload downloadAsset(int userId, int fileId);

    void storeNativeExtraFormats(Model3D model, ModelVersion version, ProviderAdapter provider, ProviderTask task);
}
