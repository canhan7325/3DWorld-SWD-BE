package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.features.ai.dto.request.*;
import com.project.w_3d_world.features.ai.dto.response.*;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Service contract for AI-driven 3D generation, tasks, wallet credits, and model library export.
 */
public interface AiGenerationService {

    AiTaskDetailResponse generateText(int userId, GenerateTextRequest request);

    AiTaskDetailResponse generateImage(int userId, String name, String prompt, MultipartFile file);

    AiTaskDetailResponse createUnifiedTask(int userId, CreateAiUnifiedTaskRequest request);

    AiTaskDetailResponse refresh(int userId, int requestId);

    AiTaskDetailResponse cancel(int userId, int requestId);

    List<AiTaskDetailResponse> list(int userId);

    List<AiTaskDetailResponse> listTasksFiltered(int userId, String status, int page, int size);

    AiTaskDetailResponse detail(int userId, int requestId);

    AiConfigOptionsResponse configOptions();

    AiEstimateResponse estimate(int userId, AiEstimateRequest request);

    AiWalletResponse wallet(int userId);

    AiWalletResponse topup(int userId, AiTopupRequest request);

    AiUploadResponse uploadSourceImage(int userId, MultipartFile file);

    List<AiUploadResponse> listUploads(int userId);

    AuthorizedModelDownload downloadUploadFile(int userId, String uploadId);

    List<AiProjectSummaryResponse> listProjects(int userId);

    AiProjectDetailResponse getProject(int userId, int modelId);

    AiProjectDetailResponse updateProject(int userId, int modelId, UpdateAiProjectRequest request);

    AiVersionDetailResponse getVersion(int userId, int versionId);

    AiDimensionsResponse computeDimensions(int userId, int versionId, AiDimensionsRequest request);

    AiExportResponse exportVersion(int userId, int versionId, AiExportRequest request);

    AuthorizedModelDownload downloadAsset(int userId, int fileId);

    Map<String, Object> handleMeshyWebhook(Map<String, Object> payload, String secret);
}
