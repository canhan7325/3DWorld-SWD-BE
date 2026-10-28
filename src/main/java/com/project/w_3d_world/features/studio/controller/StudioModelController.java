package com.project.w_3d_world.features.studio.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;
import com.project.w_3d_world.features.studio.dto.request.*;
import com.project.w_3d_world.features.studio.dto.response.*;
import com.project.w_3d_world.features.studio.service.StudioModelService;
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
@RequestMapping("/api/studio")
@RequiredArgsConstructor
@Tag(name = "3D Studio")
@SecurityRequirement(name = "bearerAuth")
public class StudioModelController {

    private final StudioModelService studioService;

    // --- 1. PROJECTS ---

    @GetMapping({"/projects", "/models"})
    @Operation(summary = "Lấy danh sách các dự án Studio 3D của người dùng")
    public ApiResponse<List<StudioProjectSummaryResponse>> listProjects(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.listProjects(userId));
    }

    @PostMapping({"/projects", "/models"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo một dự án 3D Studio mới")
    public ApiResponse<StudioProjectDetailResponse> createProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateStudioProjectRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("Studio project created successfully", studioService.createProject(userId, request));
    }

    @GetMapping({"/projects/{modelId}", "/models/{modelId}"})
    @Operation(summary = "Xem chi tiết dự án 3D Studio bao gồm cấu trúc scene và các phiên bản")
    public ApiResponse<StudioProjectDetailResponse> getProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.getProject(userId, modelId));
    }

    @PutMapping("/projects/{modelId}/content")
    @Operation(summary = "Lưu bản sửa đổi mới của scene Studio (kiểm tra xung đột qua baseRevision)")
    public ApiResponse<StudioProjectDetailResponse> saveContent(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @Valid @RequestBody SaveStudioContentRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.saveProjectContent(userId, modelId, request));
    }

    @PostMapping({"/projects/{modelId}/save", "/models/{modelId}/versions"})
    @Operation(summary = "Lưu bản sửa đổi mới của scene Studio (kiểm tra xung đột qua expectedVersion)")
    public ApiResponse<StudioProjectDetailResponse> saveProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @Valid @RequestBody SaveStudioProjectRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.saveProject(userId, modelId, request));
    }

    @PatchMapping({"/projects/{modelId}", "/models/{modelId}"})
    @Operation(summary = "Cập nhật metadata dự án 3D Studio (tên, mô tả)")
    public ApiResponse<StudioProjectDetailResponse> updateProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @Valid @RequestBody UpdateStudioProjectRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.updateProject(userId, modelId, request));
    }

    @PostMapping({"/projects/{modelId}/duplicate", "/models/{modelId}/duplicate"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nhân bản dự án Studio (duplicate scene)")
    public ApiResponse<StudioProjectDetailResponse> duplicateProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("Project duplicated successfully", studioService.duplicateProject(userId, modelId));
    }

    @DeleteMapping({"/projects/{modelId}", "/models/{modelId}"})
    @Operation(summary = "Xóa mềm dự án 3D Studio")
    public ApiResponse<java.util.Map<String, String>> deleteProject(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        studioService.deleteProject(userId, modelId);
        return ApiResponse.success(java.util.Map.of("message", "Studio project deleted successfully"));
    }

    @GetMapping({"/projects/{modelId}/thumbnail", "/models/{modelId}/thumbnail"})
    @Operation(summary = "Tải xuống hình ảnh thumbnail của dự án Studio")
    public void downloadThumbnail(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            HttpServletResponse response
    ) throws IOException {
        int userId = ModelRequestValidation.requireUserId(user);
        AuthorizedModelDownload download = studioService.downloadThumbnail(userId, modelId);
        streamDownload(download, response);
    }

    // --- 2. SNAPSHOTS ---

    @GetMapping("/projects/{modelId}/snapshots")
    @Operation(summary = "Xem lịch sử các bản snapshot/export của dự án Studio")
    public ApiResponse<List<StudioSnapshotResponse>> listSnapshots(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.listSnapshots(userId, modelId));
    }

    @PostMapping("/projects/{modelId}/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo một bản snapshot xuất file bất biến của scene Studio (JSON)")
    public ApiResponse<StudioSnapshotResponse> createSnapshot(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @RequestBody(required = false) CreateStudioSnapshotRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("Snapshot created successfully", studioService.createSnapshot(userId, modelId, request));
    }

    @PostMapping(value = "/projects/{modelId}/snapshots", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo snapshot kèm tệp STL do trình duyệt nướng (baked)")
    public ApiResponse<StudioSnapshotResponse> createSnapshotMultipart(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @RequestPart(value = "meta", required = false) CreateStudioSnapshotRequest meta,
            @RequestPart("file") MultipartFile file
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.created("Snapshot created successfully", studioService.createSnapshotWithFile(userId, modelId, meta, file));
    }

    @PatchMapping("/projects/{modelId}/snapshots/{snapshotCode}")
    @Operation(summary = "Cập nhật ghi chú/mô tả của snapshot (nội dung file giữ bất biến)")
    public ApiResponse<StudioSnapshotResponse> updateSnapshot(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @PathVariable String snapshotCode,
            @RequestBody(required = false) UpdateStudioSnapshotRequest request
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.updateSnapshot(userId, modelId, snapshotCode, request));
    }

    // --- 3. VERSIONS & FILES ---

    @GetMapping({"/projects/{modelId}/versions", "/models/{modelId}/versions"})
    @Operation(summary = "Xem danh sách các phiên bản đã lưu của dự án Studio")
    public ApiResponse<List<StudioProjectDetailResponse.StudioVersionSummary>> getVersions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.getVersions(userId, modelId));
    }

    @GetMapping({"/projects/{modelId}/versions/{versionNumber}", "/models/{modelId}/versions/{versionNumber}"})
    @Operation(summary = "Xem chi tiết một phiên bản cụ thể theo số phiên bản")
    public ApiResponse<StudioProjectDetailResponse.StudioVersionSummary> getVersion(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @PathVariable Integer versionNumber
    ) {
        int userId = ModelRequestValidation.requireUserId(user);
        return ApiResponse.success(studioService.getVersion(userId, modelId, versionNumber));
    }

    @GetMapping({"/projects/{modelId}/files/{fileId}/download", "/models/{modelId}/files/{fileId}/download"})
    @Operation(summary = "Tải xuống tệp của dự án Studio (scene JSON, GLB, v.v.)")
    public void downloadFile(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer modelId,
            @PathVariable Integer fileId,
            HttpServletResponse response
    ) throws IOException {
        int userId = ModelRequestValidation.requireUserId(user);
        AuthorizedModelDownload download = studioService.downloadFile(userId, modelId, fileId);
        streamDownload(download, response);
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
