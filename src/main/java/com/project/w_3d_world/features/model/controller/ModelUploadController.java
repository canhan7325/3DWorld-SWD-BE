package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "Model Upload")
@SecurityRequirement(name = "bearerAuth")
public class ModelUploadController {
    private final ModelUploadService uploadService;

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a new private model")
    public ResponseEntity<ApiResponse<ModelUploadResponse>> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file) {
        int userId = ModelRequestValidation.requireUserId(user);
        CreateModelRequest metadata = new CreateModelRequest(
                ModelServiceValidation.modelNameFromFilename(file == null ? null : file.getOriginalFilename()), null);
        ModelUploadResponse response = uploadService.uploadModel(userId, metadata, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Model uploaded successfully", response));
    }
}
