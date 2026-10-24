package com.project.w_3d_world.features.moderation.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.moderation.dto.ReportDtos.*;
import com.project.w_3d_world.features.moderation.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Marketplace reports", description = "Report marketplace violations and record Admin decisions.")
public class ReportController {
    private final ReportService reports;

    @PostMapping("/reports")
    @Operation(summary = "Report a violation", description = "Provide a productId or reportedUserId, reason and optional evidence reference.")
    public ApiResponse<ReportResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody CreateRequest request) {
        return ApiResponse.success(reports.create(user.userId(), request));
    }

    @GetMapping("/admin/reports")
    @Operation(summary = "List reports", description = "Admin-only report queue filtered by OPEN, RESOLVED or REJECTED.")
    public ApiResponse<MarketplacePage<ReportResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "OPEN") String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reports.list(user.userId(), status, page, size));
    }

    @PatchMapping("/admin/reports/{id}")
    @Operation(summary = "Resolve report", description = "Admin records RESOLVED/REJECTED with a reason. A resolved product report may hide the product "
                    + "without altering existing orders.")
    public ApiResponse<ReportResponse> resolve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id,
                                               @Valid @RequestBody ResolveRequest request) {
        return ApiResponse.success(reports.resolve(user.userId(), id, request));
    }
}
