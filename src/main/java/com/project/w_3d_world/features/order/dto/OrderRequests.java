package com.project.w_3d_world.features.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import java.time.Instant;
import java.util.List;

public final class OrderRequests {
    private OrderRequests() {}

    public record CheckoutRequest(
            @NotEmpty List<Integer> cartItemIds,
            String shippingAddressSnapshot,
            @NotNull Integer shippingAddressId,
            @NotBlank @Size(max = 255) String idempotencyKey,
            @Schema(
                    description = "Required when GHTK is enabled. Obtain quoteToken from POST /api/shipping/quote. "
                            + "Use the same cartItemIds and shippingAddressId before quote expiry.")
            @Size(max = 500000) String shippingQuoteToken,
            @Schema(
                    description = "COD: pay the carrier on delivery. GATEWAY or ZALOPAY: pay online. Defaults to GATEWAY.",
                    example = "COD")
            @Pattern(regexp = "COD|GATEWAY") String paymentMethod) {
        public CheckoutRequest {
            paymentMethod = paymentMethod == null ? "GATEWAY" : paymentMethod.trim().toUpperCase(Locale.ROOT);
            if ("ZALOPAY".equals(paymentMethod)) paymentMethod = "GATEWAY";
        }

        public CheckoutRequest(List<Integer> cartItemIds, String shippingAddressSnapshot,
                               Integer shippingAddressId, String idempotencyKey, String shippingQuoteToken) {
            this(cartItemIds, shippingAddressSnapshot, shippingAddressId, idempotencyKey, shippingQuoteToken, "GATEWAY");
        }
        public CheckoutRequest(List<Integer> cartItemIds, String shippingAddressSnapshot,
                               Integer shippingAddressId, String idempotencyKey) {
            this(cartItemIds, shippingAddressSnapshot, shippingAddressId, idempotencyKey, null);
        }
    }

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record HistoryResponse(int id, String fromStatus, String toStatus,
            Instant changedAt, Integer changedByUserId, String reason) {
        public static HistoryResponse from(com.project.w_3d_world.features.order.entity.OrderStatusHistory history) {
            return new HistoryResponse(history.getId(), history.getFromStatus(), history.getToStatus(),
                    history.getChangedAt(), history.getChangedByUserId(), history.getReason());
        }
    }

    public record StatusRequest(@NotBlank String status) {}
}
