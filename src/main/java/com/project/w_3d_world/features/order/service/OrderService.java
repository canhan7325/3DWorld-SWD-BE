package com.project.w_3d_world.features.order.service;

import com.project.w_3d_world.features.order.dto.request.CheckoutRequest;
import com.project.w_3d_world.features.order.dto.response.HistoryResponse;
import com.project.w_3d_world.features.order.dto.response.OrderResponse;
import java.util.List;

public interface OrderService {
    List<OrderResponse> checkout(int userId, CheckoutRequest request);
    List<OrderResponse> buyerOrders(int userId);
    List<OrderResponse> sellerOrders(int userId);
    OrderResponse updateStatus(int sellerId, int orderId, String status);
    OrderResponse detail(int userId, int orderId);
    List<HistoryResponse> history(int userId, int orderId);
    OrderResponse cancel(int userId, int orderId, String reason);
    OrderResponse confirmReceived(int userId, int orderId);
    void releaseExpiredReservations();
}
