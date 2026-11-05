package com.project.w_3d_world.features.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

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
