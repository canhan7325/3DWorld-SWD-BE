package com.project.w_3d_world.features.cart.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(Integer itemId, Integer variantId, String productTitle,
                                   String sku, BigDecimal unitPrice, int quantity,
                                   BigDecimal lineTotal, boolean unavailable,
                                   BigDecimal previousUnitPrice, boolean priceChanged, Integer productId,
                                   Integer storefrontId, String thumbnailUrl) {}
