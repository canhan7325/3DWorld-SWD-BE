package com.project.w_3d_world.features.order.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.order.dto.OrderRequests.CancelRequest;
import com.project.w_3d_world.features.order.dto.OrderRequests.CheckoutRequest;
import com.project.w_3d_world.features.order.dto.OrderRequests.HistoryResponse;
import com.project.w_3d_world.features.order.dto.OrderRequests.StatusRequest;
import com.project.w_3d_world.features.order.dto.OrderResponses.OrderResponse;
import com.project.w_3d_world.features.order.service.OrderService;
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
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Physical orders", description = "Physical checkout, order history and fulfillment transitions.")
public class OrderController {
    private final OrderService orders;

    @PostMapping("/checkout")
    @Operation(
            summary = "Checkout selected cart items",
            description = "Splits selected physical items into one child order per seller "
                    + "and reserves stock atomically.")
    public ApiResponse<List<OrderResponse>> checkout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success(orders.checkout(user.userId(), request));
    }

    @GetMapping("/mine")
    @Operation(summary = "List buyer orders", description = "Return orders owned by the signed-in buyer.")
    public ApiResponse<List<OrderResponse>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(orders.buyerOrders(user.userId()));
    }

    @GetMapping("/seller")
    @Operation(summary = "List seller orders", description = "Return orders for the signed-in seller storefront.")
    public ApiResponse<List<OrderResponse>> seller(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(orders.sellerOrders(user.userId()));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Update order status", description = "Apply a guarded seller fulfillment transition.")
    public ApiResponse<OrderResponse> status(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int orderId,
            @Valid @RequestBody StatusRequest request) {
        return ApiResponse.success(orders.updateStatus(user.userId(), orderId, request.status()));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order detail", description = "Read an order owned by the buyer/seller, or by authorized operational staff.")
    public ApiResponse<OrderResponse> detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId) {
        return ApiResponse.success(orders.detail(user.userId(), orderId));
    }

    @GetMapping("/{orderId}/history")
    @Operation(summary = "Get order status history", description = "Return persisted status changes in chronological order with ownership checks.")
    public ApiResponse<List<HistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId) {
        return ApiResponse.success(orders.history(user.userId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel unpaid order", description = "Buyer cancellation releases reserved stock atomically. Repeated cancellation is idempotent; "
                    + "paid orders require the refund workflow.")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId,
            @Valid @RequestBody CancelRequest request) {
        return ApiResponse.success(orders.cancel(user.userId(), orderId, request.reason()));
    }

    @PostMapping("/{orderId}/confirm-received")
    @Operation(summary = "Confirm order received", description = "Buyer confirms a delivered order once and starts the snapshotted refund window. This does not "
                    + "release seller funds.")
    public ApiResponse<OrderResponse> received(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable int orderId) {
        return ApiResponse.success(orders.confirmReceived(user.userId(), orderId));
    }
}
