package com.project.w_3d_world.features.refund.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.refund.dto.request.RefundRequests.*;
import com.project.w_3d_world.features.refund.service.ManualRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Manual refunds",description="Buyer evidence → staff review → admin manual transfer confirmation. Rejections remain in history.")
public class ManualRefundController {
    private final ManualRefundService service;
    @PostMapping(value="/orders/{orderId}/refunds",consumes="multipart/form-data")
    @Operation(summary="Submit full-order manual refund",description="Requires reason, verified buyer bank account and 1–5 JPEG/PNG images, each at most 5 MB. Evidence is stored in Cloudinary Refund folder.")
    public ApiResponse<Map<String,Object>> submit(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int orderId,
            @RequestParam int bankAccountId,@RequestParam String accountNumber,@RequestParam String reason,@RequestPart List<MultipartFile> images){
        return ApiResponse.success(service.submit(user.userId(),orderId,bankAccountId,accountNumber,reason,images));
    }
    @GetMapping("/refunds/{id}")
    public ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id){return ApiResponse.success(service.detail(user.userId(),id));}
    @GetMapping({"/staff/refunds","/admin/refunds"})
    @Operation(summary="Review queue and permanent refund history",description="Filter PENDING, APPROVED, REJECTED or PAID. Omit status to include all.")
    public ApiResponse<List<Map<String,Object>>> queue(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(service.queue(user.userId(),status,page,size));}
    @PatchMapping("/staff/refunds/{id}/review")
    public ApiResponse<Map<String,Object>> review(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Review request){return ApiResponse.success(service.review(user.userId(),id,request));}
    @PostMapping("/admin/refunds/{id}/transfer-details")
    @Operation(summary="View bank details for approved refund",description="Admin only; records access reason in refund history. Account number is encrypted at rest and excluded from regular responses.")
    public ApiResponse<Map<String,String>> transferDetails(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@RequestParam String reason){return ApiResponse.success(service.transferDetails(user.userId(),id,reason));}
    @PostMapping("/admin/refunds/{id}/confirm-transfer")
    @Operation(summary="Confirm successful manual bank transfer",description="Admin only, after staff approval. Records platform cash debit and reverses pending seller earnings exactly once. Requires actual transfer reference.")
    public ApiResponse<Map<String,Object>> confirm(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody Transfer request){return ApiResponse.success(service.confirm(user.userId(),id,request));}
    @PostMapping("/staff/orders/{orderId}/return-shipping")
    @Operation(summary="Record actual cancellation/return shipping fees",description="Staff/admin chooses payer and records actual outbound/return fees with carrier reconciliation reference. Cancellation fees must be borne by seller. Each order is assessed once.")
    public ApiResponse<Void> shipping(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable int orderId,@Valid @RequestBody ShippingAssessment request){service.assessCancelled(user.userId(),orderId,request);return ApiResponse.success(null);}
}
