package com.project.w_3d_world.features.ai.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.ai.dto.request.*;
import com.project.w_3d_world.features.ai.dto.response.*;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@RestController
@RequestMapping({"/api/ai", "/api/ai-studio"})
@RequiredArgsConstructor
@Tag(name = "AI Studio & Generation")
@SecurityRequirement(name = "bearerAuth")
public class AiGenerationController {

    private final AiGenerationService aiGenerationService;

    // --- 1. CONFIG & ESTIMATES ---

    @GetMapping("/config-options")
    @Operation(summary = "Lấy danh sách cấu hình, độ phân giải, và presets khả dụng")
    public ApiResponse<AiConfigOptionsResponse> configOptions() {
        return ApiResponse.success(aiGenerationService.configOptions());
    }

    @PostMapping("/estimates")
    @Operation(summary = "Ước tính chi phí credits cho cấu hình tạo 3D")
    public ApiResponse<AiEstimateResponse> estimate(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) AiEstimateRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.estimate(userId, request));
    }

    // --- 2. UPLOADS ---

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải lên ảnh nguồn cho AI generation (JPG/PNG/WEBP <= 20MB)")
    public ApiResponse<AiUploadResponse> uploadSourceImage(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("Image uploaded successfully", aiGenerationService.uploadSourceImage(userId, file));
    }

    @GetMapping("/uploads")
    @Operation(summary = "Danh sách ảnh nguồn đã tải lên của người dùng")
    public ApiResponse<List<AiUploadResponse>> listUploads(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.listUploads(userId));
    }

    @GetMapping("/uploads/{id}/file")
    @Operation(summary = "Tải xuống hoặc xem tệp ảnh nguồn đã tải lên")
    public void downloadUploadFile(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String id,
            HttpServletResponse response
    ) throws IOException {
        int userId = ModelRequestValidation.requireUserId(user);
        AuthorizedModelDownload download = aiGenerationService.downloadUploadFile(userId, id);
        streamDownload(download, response);
    }

    // --- 3. GENERATION TASKS ---

    @PostMapping(value = "/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tác vụ AI 3D thống nhất (Text-to-3D)")
    public ApiResponse<AiTaskDetailResponse> createTaskJson(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateAiUnifiedTaskRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("AI generation task initiated", aiGenerationService.createUnifiedTask(userId, request));
    }

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tác vụ AI 3D thống nhất từ ảnh (Image-to-3D)")
    public ApiResponse<AiTaskDetailResponse> createTaskMultipart(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam("file") MultipartFile file
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("AI generation task initiated", aiGenerationService.generateImage(userId, name, prompt, file));
    }

    @PostMapping({"/text-to-3d", "/models/generate/text"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mô hình 3D từ mô tả văn bản (Text-to-3D)")
    public ApiResponse<AiTaskDetailResponse> generateText(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody GenerateTextRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("AI generation task initiated", aiGenerationService.generateText(userId, request));
    }

    @PostMapping(value = {"/image-to-3d", "/models/generate/image"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mô hình 3D từ hình ảnh (Image-to-3D)")
    public ApiResponse<AiTaskDetailResponse> generateImage(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam("file") MultipartFile file
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("AI generation task initiated", aiGenerationService.generateImage(userId, name, prompt, file));
    }

    @GetMapping({"/tasks", "/models/requests"})
    @Operation(summary = "Lấy danh sách các tác vụ sinh 3D của người dùng (hỗ trợ lọc theo status, phân trang)")
    public ApiResponse<List<AiTaskDetailResponse>> listTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        if (status != null && !status.isBlank()) {
            return ApiResponse.success(aiGenerationService.listTasksFiltered(userId, status, page, size));
        }
        return ApiResponse.success(aiGenerationService.list(userId));
    }

    @GetMapping({"/tasks/{requestId}", "/models/requests/{requestId}"})
    @Operation(summary = "Xem chi tiết tiến độ tác vụ AI và các phiên bản đã tạo")
    public ApiResponse<AiTaskDetailResponse> getTask(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer requestId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.detail(userId, requestId));
    }

    @PostMapping({"/tasks/{requestId}/refresh", "/models/requests/{requestId}/refresh"})
    @Operation(summary = "Làm mới và đồng bộ trạng thái tác vụ từ AI Provider (Meshy)")
    public ApiResponse<AiTaskDetailResponse> refreshTask(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer requestId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.refresh(userId, requestId));
    }

    @PostMapping("/tasks/{requestId}/cancel")
    @Operation(summary = "Hủy tác vụ đang xử lý và hoàn trả credit")
    public ApiResponse<AiTaskDetailResponse> cancelTask(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer requestId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.cancel(userId, requestId));
    }

    // --- 4. PROJECTS, VERSIONS, ASSETS ---

    @GetMapping("/projects")
    @Operation(summary = "Lấy danh sách các dự án/mô hình AI của người dùng")
    public ApiResponse<List<AiProjectSummaryResponse>> listProjects(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.listProjects(userId));
    }

    @GetMapping("/projects/{modelId}")
    @Operation(summary = "Xem chi tiết dự án AI: metadata, tác vụ, các phiên bản 3D")
    public ApiResponse<AiProjectDetailResponse> getProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.getProject(userId, modelId));
    }

    @PatchMapping("/projects/{modelId}")
    @Operation(summary = "Cập nhật metadata của dự án AI (tên, mô tả, hiển thị)")
    public ApiResponse<AiProjectDetailResponse> updateProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @Valid @RequestBody UpdateAiProjectRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.updateProject(userId, modelId, request));
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Xem chi tiết phiên bản 3D, danh sách tệp và thông số mesh")
    public ApiResponse<AiVersionDetailResponse> getVersion(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer versionId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.getVersion(userId, versionId));
    }

    @PutMapping("/versions/{versionId}/dimensions")
    @Operation(summary = "Tính toán và xác thực kích thước hình học 3D (bounding box)")
    public ApiResponse<AiDimensionsResponse> updateDimensions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer versionId,
            @RequestBody(required = false) AiDimensionsRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.computeDimensions(userId, versionId, request));
    }

    @PostMapping("/versions/{versionId}/exports")
    @Operation(summary = "Xuất mô hình theo định dạng yêu cầu (GLB)")
    public ApiResponse<AiExportResponse> exportVersion(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer versionId,
            @RequestBody(required = false) AiExportRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.exportVersion(userId, versionId, request));
    }

    @GetMapping("/assets/{fileId}/file")
    @Operation(summary = "Tải xuống tệp tài sản 3D (có phân quyền sở hữu)")
    public void downloadAsset(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer fileId,
            HttpServletResponse response
    ) throws IOException {
        int userId = ModelRequestValidation.requireUserId(user);
        AuthorizedModelDownload download = aiGenerationService.downloadAsset(userId, fileId);
        streamDownload(download, response);
    }

    // --- 5. CREDITS & WALLET ---

    @GetMapping("/wallet")
    @Operation(summary = "Xem số dư credits AI của người dùng")
    public ApiResponse<AiWalletResponse> wallet(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.wallet(userId));
    }

    @PostMapping("/wallet/topup")
    @Operation(summary = "Nạp demo credits (chỉ hoạt động trong môi trường phát triển/kiểm thử)")
    public ApiResponse<AiWalletResponse> topup(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AiTopupRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(aiGenerationService.topup(userId, request));
    }

    // --- HELPER STREAMING ---

    private void streamDownload(AuthorizedModelDownload download, HttpServletResponse response) throws IOException {
        try (download) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(download.contentType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (download.fileSize() != null && download.fileSize() > 0) {
                response.setContentLengthLong(download.fileSize());
            }

            try (InputStream in = download.inputStream(); OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
        }
    }
}
