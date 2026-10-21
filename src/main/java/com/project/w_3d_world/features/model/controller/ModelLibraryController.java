package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.response.ModelDetailResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelResponse;
import com.project.w_3d_world.features.model.service.ModelLibraryService;
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
@Tag(name = "Model Library")
@SecurityRequirement(name = "bearerAuth")
public class ModelLibraryController {
    private final ModelLibraryService libraryService;

    @GetMapping("/my")
    @Operation(summary = "Retrieve the authenticated user's model library")
    public ApiResponse<PagedModelResponse> getMyModels(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        int userId = ModelRequestValidation.requireUserId(user);
        int pageSize = ModelRequestValidation.parsePageSize(size);
        return new ApiResponse<>(200, "Models retrieved successfully",
                libraryService.getMyModels(userId, ModelRequestValidation.parsePage(page), pageSize),
                OffsetDateTime.now());
    }

    @GetMapping("getId/{modelId}")
    @Operation(summary = "Retrieve one owned model and its latest version")
    public ApiResponse<ModelDetailResponse> getModelDetails(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId) {
        int userId = ModelRequestValidation.requireUserId(user);
        int id = ModelRequestValidation.parsePositive(modelId, "modelId");
        return new ApiResponse<>(200, "Model retrieved successfully",
                libraryService.getModelDetails(userId, id), OffsetDateTime.now());
    }
}
