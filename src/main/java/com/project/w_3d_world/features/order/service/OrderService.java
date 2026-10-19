package com.project.w_3d_world.features.order.service;

import com.project.w_3d_world.features.order.dto.OrderRequests.CheckoutRequest;
import com.project.w_3d_world.features.order.dto.OrderResponses.OrderResponse;
import java.util.List;

public interface OrderService {
    List<OrderResponse> checkout(int userId, CheckoutRequest request);
    List<OrderResponse> buyerOrders(int userId);
    List<OrderResponse> sellerOrders(int userId);
    OrderResponse updateStatus(int sellerId, int orderId, String status);
    void releaseExpiredReservations();
}
