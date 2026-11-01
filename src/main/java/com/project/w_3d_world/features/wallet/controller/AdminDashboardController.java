package com.project.w_3d_world.features.wallet.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.wallet.service.AdminDashboardService;
import com.project.w_3d_world.features.wallet.dto.request.DebtSuspensionRequest;
import com.project.w_3d_world.features.wallet.dto.request.DebtRestorationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
public class AdminDashboardController {
    private final AdminDashboardService service;
    @GetMapping("/metrics")
    @Operation(summary="Revenue cards and current shipping debt",description="Inclusive from/to dates in Asia/Ho_Chi_Minh. Realized commission uses release date; subscription uses paid date; provisional commission uses earning creation date. Debt is the current outstanding total, independent of date filters.")
    public ApiResponse<Map<String,Object>> metrics(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return ApiResponse.success(service.metrics(from,to));}
    @GetMapping("/commissions")
    public ApiResponse<List<Map<String,Object>>> commissions(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(required=false) Integer shopId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.commissions(from,to,shopId,page,size));}
    @GetMapping("/subscriptions")
    public ApiResponse<List<Map<String,Object>>> subscriptions(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(required=false) Integer shopId,
            @RequestParam(required=false) Integer userId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.subscriptions(from,to,shopId,userId,page,size));}
    @GetMapping("/shops/{id}")
    public ApiResponse<Map<String,Object>> shop(@PathVariable int id){return ApiResponse.success(service.shop(id));}
    @GetMapping("/wallets")
    @Operation(summary="Shop wallets, shipping debt and inactivity",description="100000 VND is retained within available balance, not a second deposit wallet. suspension_candidate requires debt age and order inactivity to both exceed inactiveDays.")
    public ApiResponse<List<Map<String,Object>>> wallets(@RequestParam(defaultValue="false") boolean debtOnly,
            @RequestParam(defaultValue="false") boolean lockedOnly,@RequestParam(defaultValue="30") int inactiveDays,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.wallets(debtOnly,lockedOnly,inactiveDays,page,size));}
    @GetMapping("/shops/{id}/shipping-debt")
    public ApiResponse<Map<String,Object>> debt(@PathVariable int id){return ApiResponse.success(service.debt(id));}
    @PostMapping("/shops/{id}/suspend-for-debt")
    public ApiResponse<Void> suspend(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int id,@Valid @RequestBody DebtSuspensionRequest request){service.suspend(user.userId(),id,request.inactiveDays(),request.reason());return ApiResponse.success(null);}
    @PostMapping("/shops/{id}/restore-after-debt")
    public ApiResponse<Void> restore(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int id,@Valid @RequestBody DebtRestorationRequest request){service.restore(user.userId(),id,request.reason());return ApiResponse.success(null);}
}
