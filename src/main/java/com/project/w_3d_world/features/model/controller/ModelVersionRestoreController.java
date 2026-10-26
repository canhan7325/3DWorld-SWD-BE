package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.RestoreModelVersionResponse;
import com.project.w_3d_world.features.model.service.ModelVersionRestoreService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "Model Version Restore")
@SecurityRequirement(name = "bearerAuth")
public class ModelVersionRestoreController {
    private final ModelVersionRestoreService restoreService;

    @PostMapping(value = "/{modelId}/versions/{versionId}/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Khôi phục phiên bản cũ bằng cách tạo phiên bản mô hình 3D mới")
    public ResponseEntity<ApiResponse<RestoreModelVersionResponse>> restore(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId, @PathVariable String versionId,
            @Valid @RequestBody RestoreModelVersionRequest request) {
        int userId = ModelRequestValidation.requireUserId(user);
        int id = ModelRequestValidation.parsePositive(modelId, "modelId");
        int sourceId = ModelRequestValidation.parsePositive(versionId, "versionId");
        var restored = restoreService.restoreVersion(userId, id, sourceId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Model version restored successfully", restored));
    }
}
