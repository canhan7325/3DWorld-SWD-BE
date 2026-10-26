package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Model Download")
@SecurityRequirement(name = "bearerAuth")
public class ModelDownloadController {
    private static final int BUFFER_SIZE = 64 * 1024;
    private final ModelDownloadService downloadService;

    @GetMapping("/{modelId}/files/{fileId}/download")
    @Operation(summary = "Tải tệp mô hình 3D được phép truy cập")
    public void download(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId,
            @PathVariable String fileId,
            HttpServletResponse response) {
        int userId = ModelRequestValidation.requireUserId(user);
        int parsedModelId = ModelRequestValidation.parseDownloadId(modelId, "Model ID");
        int parsedFileId = ModelRequestValidation.parseDownloadId(fileId, "File ID");
        AuthorizedModelDownload download = downloadService.openAuthorizedDownload(
                userId, parsedModelId, parsedFileId);
        try (download) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(download.contentType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            try {
                while ((read = download.inputStream().read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, read);
                }
                response.getOutputStream().flush();
            } catch (IOException exception) {
                if (response.isCommitted()) {
                    log.warn("Model download stream terminated after response commitment: {}", exception.getClass().getSimpleName());
                } else {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The model file could not be streamed.");
                }
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The model file could not be closed.");
        }
    }

}
