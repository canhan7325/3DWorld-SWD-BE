package com.project.w_3d_world.features.cart.service;

import com.project.w_3d_world.features.cart.dto.CartRequests.ItemRequest;
import com.project.w_3d_world.features.cart.dto.CartResponses.CartResponse;

public interface CartService {
    CartResponse get(int userId);

    CartResponse add(int userId, ItemRequest request);

    CartResponse update(int userId, int itemId, int quantity);

    CartResponse remove(int userId, int itemId);

    CartResponse revalidate(int userId);
}
