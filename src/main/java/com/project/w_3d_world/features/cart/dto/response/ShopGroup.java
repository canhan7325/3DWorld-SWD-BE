package com.project.w_3d_world.features.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ShopGroup(Integer storefrontId, List<CartItemResponse> items, BigDecimal subtotal) {}
