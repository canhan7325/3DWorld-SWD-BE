package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.response.ModelVersionDetailResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelVersionsResponse;
import com.project.w_3d_world.features.model.service.ModelVersionHistoryService;
import com.project.w_3d_world.features.model.validation.ModelRequestValidation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "Model Version History")
@SecurityRequirement(name = "bearerAuth")
public class ModelVersionHistoryController {
    private final ModelVersionHistoryService versionService;

    @GetMapping("getId/{modelId}/versions")
    @Operation(summary = "Retrieve the version history of an owned model")
    public ApiResponse<PagedModelVersionsResponse> getVersions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        int userId = ModelRequestValidation.requireUserId(user);
        int parsedModelId = ModelRequestValidation.parsePositive(modelId, "Model ID");
        int parsedPage = ModelRequestValidation.parsePage(page);
        int parsedSize = ModelRequestValidation.parsePageSize(size);
        return new ApiResponse<>(200, "Model versions retrieved successfully",
                versionService.getModelVersions(userId, parsedModelId, parsedPage, parsedSize), OffsetDateTime.now());
    }

    @GetMapping("/{modelId}/versions/{versionId}")
    @Operation(summary = "Retrieve version details and safe file metadata")
    public ApiResponse<ModelVersionDetailResponse> getVersionDetails(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId,
            @PathVariable String versionId) {
        int userId = ModelRequestValidation.requireUserId(user);
        return new ApiResponse<>(200, "Model version retrieved successfully",
                versionService.getModelVersionDetails(userId,
                        ModelRequestValidation.parsePositive(modelId, "Model ID"),
                        ModelRequestValidation.parsePositive(versionId, "Version ID")), OffsetDateTime.now());
    }
}
