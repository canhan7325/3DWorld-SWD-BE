package com.project.w_3d_world.features.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class OrderRequests {
    private OrderRequests() {}

    public record CheckoutRequest(
            @NotEmpty List<Integer> cartItemIds,
            @NotBlank String shippingAddressSnapshot,
            Integer shippingAddressId) {}

    public record StatusRequest(@NotBlank String status) {}
}
