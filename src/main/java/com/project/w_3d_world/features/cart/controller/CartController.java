package com.project.w_3d_world.features.cart.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.cart.dto.CartRequests.ItemRequest;
import com.project.w_3d_world.features.cart.dto.CartResponses.CartResponse;
import com.project.w_3d_world.features.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "Manage the signed-in user's physical product cart.")
public class CartController {
    private final CartService cart;

    @GetMapping
    @Operation(
            summary = "Get cart",
            description = "Return current cart items and mark unavailable items without deleting them.")
    public ApiResponse<CartResponse> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(cart.get(user.userId()));
    }

    @PostMapping("/items")
    @Operation(
            summary = "Add cart item",
            description = "Add a physical variant to the cart and merge duplicate variants.")
    public ApiResponse<CartResponse> add(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ItemRequest request) {
        return ApiResponse.success(cart.add(user.userId(), request));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(
            summary = "Update cart quantity",
            description = "Update quantity after checking current available stock.")
    public ApiResponse<CartResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int itemId,
            @Valid @RequestBody QuantityRequest request) {
        return ApiResponse.success(cart.update(user.userId(), itemId, request.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove cart item", description = "Remove an item owned by the signed-in user.")
    public ApiResponse<CartResponse> remove(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable int itemId) {
        return ApiResponse.success(cart.remove(user.userId(), itemId));
    }

    @PostMapping("/revalidate")
    @Operation(
            summary = "Revalidate cart",
            description = "Recheck product status, variant status and current stock before checkout.")
    public ApiResponse<CartResponse> revalidate(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(cart.revalidate(user.userId()));
    }

    public record QuantityRequest(@Min(1) int quantity) {}
}
