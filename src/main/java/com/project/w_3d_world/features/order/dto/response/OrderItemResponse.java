package com.project.w_3d_world.features.order.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(Integer id, Integer variantId, String sku, int quantity,
                                    BigDecimal unitPrice, BigDecimal lineTotal, String productTitle, String attributes) {}
