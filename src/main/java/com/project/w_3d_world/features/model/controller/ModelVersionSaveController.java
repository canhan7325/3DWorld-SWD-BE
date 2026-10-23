package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.SaveModelVersionResponse;
import com.project.w_3d_world.features.model.service.ModelVersionSaveService;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "Model Version Save")
@SecurityRequirement(name = "bearerAuth")
public class ModelVersionSaveController {
    private final ModelVersionSaveService saveService;

    @PostMapping(value = "/{modelId}/versions/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Save a new version of an owned model")
    public ResponseEntity<ApiResponse<SaveModelVersionResponse>> save(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId,
            @Valid @RequestPart("metadata") SaveModelVersionRequest metadata,
            @RequestPart("file") MultipartFile file) {
        int userId = ModelRequestValidation.requireUserId(user);
        int id = ModelRequestValidation.parsePositive(modelId, "modelId");
        SaveModelVersionResponse saved = saveService.saveVersion(userId, id, metadata, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Model version saved successfully", saved));
    }
}
