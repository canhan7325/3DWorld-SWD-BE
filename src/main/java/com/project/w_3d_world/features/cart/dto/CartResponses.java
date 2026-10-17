package com.project.w_3d_world.features.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CartResponses {
    private CartResponses() {}

    public record CartItemResponse(Integer itemId, Integer variantId, String productTitle,
                                   String sku, BigDecimal unitPrice, int quantity,
                                   BigDecimal lineTotal, boolean unavailable) {}

    public record CartResponse(Integer cartId, List<CartItemResponse> items, BigDecimal total) {}
}
