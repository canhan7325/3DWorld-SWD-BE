package com.project.w_3d_world.features.order.dto;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderResponses {
    private OrderResponses() {}

    public record OrderItemResponse(Integer id, Integer variantId, String sku, int quantity,
                                    BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record OrderResponse(Integer id, Integer storefrontId, BigDecimal subtotal,
                                BigDecimal shippingFee, BigDecimal totalAmount, String status,
                                Instant createdAt, List<OrderItemResponse> items,
                                String orderCode, UUID checkoutGroupId,
                                Instant reservationExpiresAt, String reservationStatus,
                                BigDecimal commissionRateSnapshot, Integer refundWindowHoursSnapshot,
                                Integer autoConfirmHoursSnapshot, String shippingAddressSnapshot) {
        public static OrderResponse from(PhysicalOrder order, List<OrderItemResponse> items) {
            return new OrderResponse(order.getId(), order.getStorefrontId(), order.getSubtotal(),
                    order.getShippingFee(), order.getTotalAmount(), order.getStatus(),
                    order.getCreatedAt(), items, order.getOrderCode(), order.getCheckoutGroupId(),
                    order.getReservationExpiresAt(), order.getReservationStatus(),
                    order.getCommissionRateSnapshot(), order.getRefundWindowHoursSnapshot(),
                    order.getAutoConfirmHoursSnapshot(), order.getAddressSnapshot());
        }
    }
}
