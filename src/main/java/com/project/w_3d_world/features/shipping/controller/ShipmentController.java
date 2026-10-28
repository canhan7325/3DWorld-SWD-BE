package com.project.w_3d_world.features.shipping.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.shipping.dto.ShipmentRequests.CreateShipmentRequest;
import com.project.w_3d_world.features.shipping.dto.ShipmentRequests.EventRequest;
import com.project.w_3d_world.features.shipping.dto.ShipmentResponses.ShipmentResponse;
import com.project.w_3d_world.features.shipping.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shipments", description = "Shipment creation, tracking and delivery events.")
public class ShipmentController {
    private final ShipmentService shipments;

    @PostMapping("/orders/{orderId}/shipment")
    @Operation(
            summary = "Create shipment",
            description = "Create a demo shipment for a PROCESSING order, including accepted COD orders. Shipping does not record COD collection or settlement.")
    public ApiResponse<ShipmentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int orderId,
            @Valid @RequestBody CreateShipmentRequest request) {
        return ApiResponse.success(shipments.create(user.userId(), orderId, request));
    }

    @GetMapping("/orders/{orderId}/shipments")
    @Operation(
            summary = "Track order shipments",
            description = "Return shipment records visible to the buyer or seller.")
    public ApiResponse<List<ShipmentResponse>> buyer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int orderId) {
        return ApiResponse.success(shipments.listForBuyer(user.userId(), orderId));
    }

    @GetMapping("/seller/orders/{orderId}/shipments")
    @Operation(
            summary = "List seller shipment events",
            description = "Return shipment records for a seller-owned order.")
    public ApiResponse<List<ShipmentResponse>> seller(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int orderId) {
        return ApiResponse.success(shipments.listForSeller(user.userId(), orderId));
    }

    @PatchMapping("/shipments/{shipmentId}/event")
    @Operation(
            summary = "Apply shipment event",
            description = "Apply DELIVERED, FAILED or RETURNED to a seller-owned shipment. RETURNED charges the seller twice the snapshotted shipping fee once; insufficient available balance becomes returnShippingDebt. No return charge for cancellation before shipment.")
    public ApiResponse<ShipmentResponse> event(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int shipmentId,
            @Valid @RequestBody EventRequest request) {
        return ApiResponse.success(shipments.applyEvent(user.userId(), shipmentId, request.event()));
    }
}
