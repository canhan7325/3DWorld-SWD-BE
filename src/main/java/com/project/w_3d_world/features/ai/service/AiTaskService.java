package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.features.ai.dto.request.AiEstimateRequest;
import com.project.w_3d_world.features.ai.dto.request.CreateAiUnifiedTaskRequest;
import com.project.w_3d_world.features.ai.dto.request.GenerateTextRequest;
import com.project.w_3d_world.features.ai.dto.response.AiConfigOptionsResponse;
import com.project.w_3d_world.features.ai.dto.response.AiEstimateResponse;
import com.project.w_3d_world.features.ai.dto.response.AiTaskDetailResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AiTaskService {

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

    Map<String, Object> handleMeshyWebhook(Map<String, Object> payload, String secret);
}
