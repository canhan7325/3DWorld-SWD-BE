package com.project.w_3d_world.features.moderation.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.catalog.dto.MarketplacePage;
import com.project.w_3d_world.features.catalog.dto.SellerProductDtos.*;
import com.project.w_3d_world.features.catalog.service.SellerProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Product moderation", description = "Admin review of submitted physical products, protected against stale decisions.")
public class ProductModerationController {
    private final SellerProductService products;

    @GetMapping("/pending")
    @Operation(summary = "List pending products", description = "Admin-only manual moderation queue with pagination.")
    public ApiResponse<MarketplacePage<ProductView>> pending(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(products.pending(user.userId(), page, size));
    }

    @GetMapping("/{id}/moderation")
    @Operation(summary = "Get moderation evidence", description = "Read current content and revision. Copy revision into approval/rejection request.")
    public ApiResponse<ProductView> detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id) {
        return ApiResponse.success(products.moderationDetail(user.userId(), id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve product", description = "Publish only the current pending revision from an eligible shop. Self-review is prohibited.")
    public ApiResponse<ProductView> approve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id,
                                            @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.success(products.moderate(user.userId(), id, request, true));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject product", description = "Reject the current pending revision and record a mandatory decision reason.")
    public ApiResponse<ProductView> reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int id,
                                           @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.success(products.moderate(user.userId(), id, request, false));
    }
}
