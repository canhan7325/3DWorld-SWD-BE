package com.project.w_3d_world.features.payment.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PaymentResponse;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PodPaymentStatusResponse;
import com.project.w_3d_world.features.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Sandbox payment creation and provider callbacks.")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/zalopay")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create a ZaloPay payment",
            description = "Creates one payment obligation and one ZaloPay attempt.")
    public ApiResponse<PaymentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.success(paymentService.create(user.userId(), request));
    }

@GetMapping("/{transactionId}/status")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Operation(
        summary = "Query verified payment status",
        description = "Queries ZaloPay and applies a verified payment result.")
public ApiResponse<PaymentResponse> status(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable int transactionId) {
    return ApiResponse.success(paymentService.status(user.userId(), transactionId));
}

@GetMapping("/print-requests/{printRequestId}")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Operation(summary = "Read the authenticated payer's POD payment status")
public ApiResponse<PodPaymentStatusResponse> getPodStatus(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable int printRequestId) {
    return ApiResponse.success(paymentService.getPodStatus(user.userId(), printRequestId));
}
  
    @PostMapping("/{transactionId}/refund")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Request a ZaloPay refund",
            description = "Creates a provider refund request for a successful payment.")
    public ApiResponse<PaymentResponse> refund(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int transactionId,
            @RequestBody RefundRequest request) {
        return ApiResponse.success(paymentService.refund(
                user.userId(), transactionId, request.amount(), request.reason()));
    }

    @PostMapping("/zalopay/callback")
    @Operation(
            summary = "Receive ZaloPay callback",
            description = "Verifies the callback MAC and applies the payment effect once.")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody CallbackRequest request) {
        paymentService.callback(request.data(), request.mac());
        return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "success"));
    }

    public record CallbackRequest(String data, String mac) {}

    public record RefundRequest(BigDecimal amount, String reason) {}
}
