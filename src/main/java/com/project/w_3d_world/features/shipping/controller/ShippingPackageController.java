package com.project.w_3d_world.features.shipping.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.shipping.dto.ShippingPackageDtos.*;
import com.project.w_3d_world.features.shipping.service.ShippingPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shipping packages", description = "Prepare and read package snapshots for physical order fulfillment.")
public class ShippingPackageController {
    private final ShippingPackageService packages;

    @GetMapping("/shipping/packaging-profiles")
    @Operation(summary = "Get packaging profiles", description = "Return active packaging profiles for Seller/Admin/Print Staff.")
    public ApiResponse<List<ProfileResponse>> profiles(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(packages.profiles(user.userId()));
    }

    @GetMapping("/orders/{orderId}/packages")
    @Operation(summary = "Get order packages", description = "Buyer, seller or authorized staff may read persisted package/item snapshots.")
    public ApiResponse<List<PackageResponse>> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId) {
        return ApiResponse.success(packages.list(user.userId(), orderId));
    }

    @PostMapping("/orders/{orderId}/packages")
    @Operation(summary = "Prepare order packages", description = "Seller/staff replaces packages before shipment. Allocate every ordered unit exactly once. "
                    + "Capacity, weight, dimensions, packing group and fragile support are checked; paid order totals "
                    + "stay unchanged.")
    public ApiResponse<List<PackageResponse>> save(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId,
                                                  @Valid @RequestBody SaveRequest request) {
        return ApiResponse.success(packages.save(user.userId(), orderId, request));
    }
}
