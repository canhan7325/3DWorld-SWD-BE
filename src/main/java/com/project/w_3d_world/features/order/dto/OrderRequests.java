package com.project.w_3d_world.features.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class OrderRequests {
    private OrderRequests() {}

    public record CheckoutRequest(
            @NotEmpty List<Integer> cartItemIds,
            String shippingAddressSnapshot,
            @NotNull Integer shippingAddressId,
            @NotBlank @Size(max = 255) String idempotencyKey) {}

    public record StatusRequest(@NotBlank String status) {}
}
