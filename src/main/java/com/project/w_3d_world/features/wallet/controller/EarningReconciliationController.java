package com.project.w_3d_world.features.wallet.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.wallet.service.EarningReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance/orders/{orderId}/earning")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
public class EarningReconciliationController {
    private final EarningReconciliationService service;

    @GetMapping
    @Operation(summary="Explain pending/recognized commission",description="Returns receipt/deadline/release timestamps and blockers; dashboard recognition uses released_at, not order creation date.")
    public ApiResponse<Map<String,Object>> status(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int orderId){return ApiResponse.success(service.status(user.userId(),orderId));}
    @PostMapping("/reconcile")
    @Operation(summary="Retry eligible earning release",description="Retains all financial checks. Returns recognition status and remaining blockers; does not fabricate payment receipt or force commission recognition.")
    public ApiResponse<Map<String,Object>> reconcile(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int orderId){return ApiResponse.success(service.reconcile(user.userId(),orderId));}
}
