package com.project.w_3d_world.features.payment.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PaymentResponse;
import com.project.w_3d_world.features.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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
}
