package com.project.w_3d_world.features.studio.service;

import com.project.w_3d_world.features.studio.dto.request.CreateStudioSnapshotRequest;
import com.project.w_3d_world.features.studio.dto.request.UpdateStudioSnapshotRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioSnapshotResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StudioSnapshotService {

    List<StudioSnapshotResponse> listSnapshots(int userId, int modelId);

    StudioSnapshotResponse createSnapshot(int userId, int modelId, CreateStudioSnapshotRequest request);

    StudioSnapshotResponse createSnapshotWithFile(int userId, int modelId, CreateStudioSnapshotRequest meta, MultipartFile file);

    StudioSnapshotResponse updateSnapshot(int userId, int modelId, String snapshotCode, UpdateStudioSnapshotRequest request);
}
