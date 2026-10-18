package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.response.ModelDetailResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelResponse;
import com.project.w_3d_world.features.model.service.ModelLibraryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
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
        if (user == null) throw new ApiException(
                HttpStatus.UNAUTHORIZED, "Authentication is required.");
        int pageSize = parsePositive(size, "size");
        if (pageSize > 50) throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        return new ApiResponse<>(200, "Models retrieved successfully",
                libraryService.getMyModels(user.userId(), parseNonNegative(page, "page"), pageSize),
                OffsetDateTime.now());
    }

    @GetMapping("/{modelId}")
    @Operation(summary = "Retrieve one owned model and its latest version")
    public ApiResponse<ModelDetailResponse> getModelDetails(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId) {
        if (user == null) throw new ApiException(
                HttpStatus.UNAUTHORIZED, "Authentication is required.");
        int id = parsePositive(modelId, "modelId");
        return new ApiResponse<>(200, "Model retrieved successfully",
                libraryService.getModelDetails(user.userId(), id), OffsetDateTime.now());
    }

    private static int parseNonNegative(String value, String name) {
        int parsed = parseInteger(value, name);
        if (parsed < 0) throw ApiException.invalidRequest(name + " must be greater than or equal to 0.");
        return parsed;
    }

    private static int parsePositive(String value, String name) {
        int parsed = parseInteger(value, name);
        if (parsed <= 0) throw ApiException.invalidRequest(name + " must be a positive integer.");
        return parsed;
    }

    private static int parseInteger(String value, String name) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw ApiException.invalidRequest(name + " must be a valid integer.");
        }
    }
}
