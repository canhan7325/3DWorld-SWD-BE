package com.project.w_3d_world.features.payment.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.payment.dto.CodDtos.*;
import com.project.w_3d_world.features.payment.service.CodPaymentService;
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
@Tag(name = "COD payments", description = "Cash-on-delivery collection and platform settlement. Manual sandbox evidence, separate from delivery events and wallet release.")
public class CodPaymentController {
    private final CodPaymentService cod;

    @GetMapping("/orders/{orderId}/cod-payment")
    @Operation(summary = "Get COD payment status",
            description = "Buyer, shop owner or authorized staff can inspect expected total, amount still to collect and collection/settlement timestamps. No ZaloPay is needed.")
    public ApiResponse<CodResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId) {
        return ApiResponse.success(cod.get(user.userId(), orderId));
    }

    @PostMapping("/admin/orders/{orderId}/cod/collection")
    @Operation(summary = "Record COD collected by carrier",
            description = "Admin/Print Staff records full amount, occurredAt, unique carrier reference, evidenceKey and reason after delivery. Collection does not mark money settled or credit a seller wallet. Identical retries are idempotent; mismatches return 409.")
    public ApiResponse<CodResponse> collect(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId,
                                            @Valid @RequestBody EvidenceRequest request) {
        return ApiResponse.success(cod.collect(user.userId(), orderId, request));
    }

    @PostMapping("/admin/orders/{orderId}/cod/settlement")
    @Operation(summary = "Record COD transferred to platform",
            description = "Admin verifies settlement evidence after collection. Amount must match the order total including shipping. Reused settlement references cannot settle another order. Does not release earnings or mark the order financially complete.")
    public ApiResponse<CodResponse> settle(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId,
                                           @Valid @RequestBody EvidenceRequest request) {
        return ApiResponse.success(cod.settle(user.userId(), orderId, request));
    }
}
