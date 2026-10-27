package com.project.w_3d_world.features.shipping.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.shipping.ghtk.GhtkAddressService;
import com.project.w_3d_world.features.shipping.ghtk.GhtkAddressService.*;
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
@Tag(name = "GHTK address mapping", description = "Preserve current administrative addresses and configure explicit GHTK-compatible geography per address.")
public class GhtkAddressController {
    private final GhtkAddressService addresses;

    @GetMapping("/shipping/addresses/{addressId}/ghtk-address")
    @Operation(summary = "Preview own GHTK address", description = "Return original and carrier geography for an owned address. ORIGINAL means no override; MAPPED means an explicit Admin mapping. This does not confirm GHTK delivery coverage.")
    public ApiResponse<Resolution> preview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int addressId) {
        return ApiResponse.success(addresses.preview(user.userId(), addressId, false));
    }

    @GetMapping("/admin/shipping/addresses/{addressId}/ghtk-address")
    @Operation(summary = "Inspect address mapping as Admin", description = "Admin can inspect the buyer delivery or seller pickup address before configuring its GHTK geography.")
    public ApiResponse<Resolution> inspect(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int addressId) {
        return ApiResponse.success(addresses.preview(user.userId(), addressId, true));
    }

    @PutMapping("/admin/shipping/addresses/{addressId}/ghtk-address")
    @Operation(summary = "Configure GHTK address mapping",
            description = "Admin supplies province, legacy district, ward and reason accepted by GHTK for this exact address. "
                    + "Stores the mapping in platform_configs and audits the change. Does not alter user_addresses. "
                    + "No automatic inference for merged wards. If the original address changes, the mapping must "
                    + "be updated and previous shipping quotes become invalid.")
    public ApiResponse<Resolution> save(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int addressId,
                                        @Valid @RequestBody MappingRequest request) {
        return ApiResponse.success(addresses.save(user.userId(), addressId, request));
    }

    @DeleteMapping("/admin/shipping/addresses/{addressId}/ghtk-address")
    @Operation(summary = "Remove GHTK address mapping", description = "Admin removes the override. Future shipping quotes use the original address again.")
    public ApiResponse<Void> remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int addressId) {
        addresses.remove(user.userId(), addressId);
        return ApiResponse.success(null);
    }
}
