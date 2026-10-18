package com.project.w_3d_world.features.model.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.model.dto.response.ModelVersionDetailResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelVersionsResponse;
import com.project.w_3d_world.features.model.service.ModelVersionHistoryService;

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
@Tag(name = "Model Version History")
@SecurityRequirement(name = "bearerAuth")
public class ModelVersionHistoryController {
    private final ModelVersionHistoryService versionService;

    @GetMapping("/{modelId}/versions")
    @Operation(summary = "Retrieve the version history of an owned model")
    public ApiResponse<PagedModelVersionsResponse> getVersions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        requireUser(user);
        int parsedModelId = parsePositive(modelId, "Model ID");
        int parsedPage = parseNonNegative(page, "page");
        int parsedSize = parsePositive(size, "size");
        if (parsedSize > 50) throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        return new ApiResponse<>(200, "Model versions retrieved successfully",
                versionService.getModelVersions(user.userId(), parsedModelId, parsedPage, parsedSize), OffsetDateTime.now());
    }

    @GetMapping("/{modelId}/versions/{versionId}")
    @Operation(summary = "Retrieve version details and safe file metadata")
    public ApiResponse<ModelVersionDetailResponse> getVersionDetails(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String modelId,
            @PathVariable String versionId) {
        requireUser(user);
        return new ApiResponse<>(200, "Model version retrieved successfully",
                versionService.getModelVersionDetails(user.userId(), parsePositive(modelId, "Model ID"),
                        parsePositive(versionId, "Version ID")), OffsetDateTime.now());
    }

    private static void requireUser(AuthenticatedUser user) {
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }
    private static int parseNonNegative(String value, String name) {
        int result = parseInteger(value, name);
        if (result < 0) throw ApiException.invalidRequest(name + " must be greater than or equal to 0.");
        return result;
    }
    private static int parsePositive(String value, String name) {
        int result = parseInteger(value, name);
        if (result <= 0) throw ApiException.invalidRequest(name + " must be a positive integer.");
        return result;
    }
    private static int parseInteger(String value, String name) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw ApiException.invalidRequest(name + " must be a valid integer."); }
    }
}
