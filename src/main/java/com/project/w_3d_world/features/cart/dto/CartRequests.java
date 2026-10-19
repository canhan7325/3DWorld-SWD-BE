package com.project.w_3d_world.features.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CartRequests {
    private CartRequests() {}

    public record ItemRequest(@NotNull Integer variantId, @Min(1) int quantity) {}
}
