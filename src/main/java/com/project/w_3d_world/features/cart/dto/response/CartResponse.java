package com.project.w_3d_world.features.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Integer cartId, List<CartItemResponse> items, BigDecimal total,
                               List<ShopGroup> groups) {}
