package com.project.w_3d_world.features.wallet.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.wallet.service.AdminFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Admin finance")
public class AdminFinanceController {
    private final AdminFinanceService service;
    @GetMapping("/orders/{id}")
    @Operation(summary="Actual receipts and realized commission per order",description="Actual receipts = platform received minus paid buyer refunds. Commission is realized only after seller revenue is released for a successful received order. Shipping debt is separate.")
    public ApiResponse<Map<String,Object>> order(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int id){return ApiResponse.success(service.order(user.userId(),id));}
    @GetMapping("/orders")
    public ApiResponse<List<Map<String,Object>>> orders(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.orders(user.userId(),page,size));}
    @GetMapping("/packages")
    @Operation(summary="Paid subscription/package income",description="Groups successful platform payments by purpose and stage; separates fulfilled income from receipts requiring reconciliation. Wallet top-ups are excluded.")
    public ApiResponse<List<Map<String,Object>>> packages(@AuthenticationPrincipal AuthenticatedUser user){return ApiResponse.success(service.packages(user.userId()));}
    @GetMapping("/cash")
    @Operation(summary="Recorded platform cash flow",description="Receipts including wallet top-ups minus confirmed refunds and completed seller payouts. This is recorded cash flow, not a live bank balance or profit; external carrier payments and bank fees are not imported.")
    public ApiResponse<Map<String,Object>> cash(@AuthenticationPrincipal AuthenticatedUser user){return ApiResponse.success(service.cash(user.userId()));}
}
