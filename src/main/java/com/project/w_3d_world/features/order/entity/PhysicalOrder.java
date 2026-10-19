package com.project.w_3d_world.features.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "physical_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "physical_order_id") private Integer id;
    @Column(name = "buyer_user_id", nullable = false) private Integer buyerUserId;
    @Column(name = "storefront_id", nullable = false) private Integer storefrontId;
    @Column(name = "shipping_address_id") private Integer shippingAddressId;
    @Column(name = "shipping_address_snapshot", nullable = false, columnDefinition = "text")
    private String addressSnapshot;
    @Column(name = "checkout_group_id") private UUID checkoutGroupId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal;
    @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2) private BigDecimal shippingFee;
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(name = "order_status", nullable = false) private String status = "CREATED";
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "received_at") private Instant receivedAt;
    @Column(name = "refund_deadline_at") private Instant refundDeadlineAt;
    @Column(name = "financial_completed_at") private Instant financialCompletedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public PhysicalOrder(Integer buyerUserId, Integer storefrontId, String addressSnapshot,
                         UUID checkoutGroupId, BigDecimal subtotal, BigDecimal shippingFee) {
        this.buyerUserId = buyerUserId;
        this.storefrontId = storefrontId;
        this.addressSnapshot = addressSnapshot;
        this.checkoutGroupId = checkoutGroupId;
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.totalAmount = subtotal.add(shippingFee);
    }

    public void setShippingAddressId(Integer shippingAddressId) {
        this.shippingAddressId = shippingAddressId;
    }

    public void transition(String nextStatus) {
        if (!allowed(nextStatus)) throw new IllegalArgumentException("Invalid order status transition.");
        status = nextStatus;
        if ("DELIVERED".equals(nextStatus)) deliveredAt = Instant.now();
        if ("RECEIVED".equals(nextStatus)) receivedAt = Instant.now();
    }

    private boolean allowed(String next) {
        return switch (status) {
            case "CREATED" -> next.equals("PENDING_PAYMENT") || next.equals("CANCELLED");
            case "PENDING_PAYMENT" -> next.equals("PAID") || next.equals("CANCELLED");
            case "PAID" -> next.equals("PROCESSING") || next.equals("CANCELLED");
            case "PROCESSING" -> next.equals("SHIPPED") || next.equals("CANCELLED");
            case "SHIPPED" -> next.equals("DELIVERED") || next.equals("RETURNED");
            case "DELIVERED" -> next.equals("RECEIVED") || next.equals("RETURNED");
            default -> false;
        };
    }
}
