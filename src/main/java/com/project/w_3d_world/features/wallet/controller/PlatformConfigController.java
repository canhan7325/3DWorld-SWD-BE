package com.project.w_3d_world.features.wallet.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.wallet.dto.PlatformConfigDtos.UpdateRequest;
import com.project.w_3d_world.features.wallet.service.PlatformConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/platform-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Platform configuration", description="Admin configuration for V1 commission and new storefront subscription price.")
public class PlatformConfigController {
    private final PlatformConfigService service;
    @GetMapping @Operation(summary="Get marketplace configuration")
    public ApiResponse<Map<String,BigDecimal>> get(@AuthenticationPrincipal AuthenticatedUser user) { return ApiResponse.success(service.get(user.userId())); }
    @PutMapping @Operation(summary="Update marketplace configuration", description="Commission rate (0-1, e.g. 0.05 = 5%) and monthly fee affect future orders/subscriptions only. Existing snapshots do not change.")
    public ApiResponse<Map<String,BigDecimal>> update(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdateRequest request) { return ApiResponse.success(service.update(user.userId(), request.commissionRate(), request.storefrontMonthlyFee())); }
}
