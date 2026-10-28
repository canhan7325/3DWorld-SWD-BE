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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "physical_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "physical_order_id")
    private Integer id;
    @Column(name = "buyer_user_id", nullable = false)
    private Integer buyerUserId;
    @Column(name = "storefront_id", nullable = false)
    private Integer storefrontId;
    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address_snapshot", nullable = false, columnDefinition = "jsonb")
    private String addressSnapshot;
    @Column(name = "checkout_group_id")
    private UUID checkoutGroupId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingFee;
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "order_status", nullable = false)
    private String status = "CREATED";
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "received_at")
    private Instant receivedAt;
    @Column(name = "refund_deadline_at")
    private Instant refundDeadlineAt;
    @Column(name = "financial_completed_at")
    private Instant financialCompletedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "order_code", length = 80, unique = true)
    private String orderCode;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 255)
    private String requestFingerprint;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Column(name = "reservation_status", length = 30, nullable = false)
    private String reservationStatus = "NONE";

    @Column(name = "stock_consumed_at")
    private Instant stockConsumedAt;

    @Column(name = "stock_released_at")
    private Instant stockReleasedAt;

    @Column(name = "stock_restocked_at")
    private Instant stockRestockedAt;

    @Column(name = "commission_rate_snapshot", precision = 7, scale = 5)
    private BigDecimal commissionRateSnapshot;

    @Column(name = "refund_window_hours_snapshot")
    private Integer refundWindowHoursSnapshot;

    @Column(name = "auto_confirm_hours_snapshot")
    private Integer autoConfirmHoursSnapshot;

    @Column(name = "auto_confirm_due_at")
    private Instant autoConfirmDueAt;

    @Column(name = "payment_method_snapshot", length = 30)
    private String paymentMethodSnapshot;

    @Column(name = "shipping_calculation_version", length = 50)
    private String shippingCalculationVersion;

    @Column(name = "shipping_package_count")
    private Integer shippingPackageCount;

    @Column(name = "shipping_packaging_cost_vnd", precision = 18, scale = 2)
    private BigDecimal shippingPackagingCostVnd;

    @Column(name = "shipping_packaging_weight_grams")
    private Integer shippingPackagingWeightGrams;

    @Column(name = "shipping_product_weight_grams")
    private Integer shippingProductWeightGrams;

    @Column(name = "shipping_weight_grams")
    private Integer shippingWeightGrams;

    @Column(name = "shipping_quoted_at")
    private Instant shippingQuotedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
        if (!allowed(nextStatus)) {
            throw new IllegalArgumentException("Invalid order status transition.");
        }
        status = nextStatus;
        updatedAt = Instant.now();
        if ("CANCELLED".equals(nextStatus)) {
            cancelledAt = Instant.now();
        }
        if ("DELIVERED".equals(nextStatus)) {
            deliveredAt = Instant.now();
        }
        if ("RECEIVED".equals(nextStatus)) {
            receivedAt = Instant.now();
        }
    }

    private boolean allowed(String next) {
        return switch (status) {
            case "CREATED" -> next.equals("PENDING_PAYMENT") || next.equals("CANCELLED")
                    || (isCod() && next.equals("PROCESSING"));
            case "PENDING_PAYMENT" -> next.equals("PAID") || next.equals("CANCELLED");
            case "PAID" -> next.equals("PROCESSING") || next.equals("CANCELLED");
            case "PROCESSING" -> next.equals("SHIPPED") || next.equals("CANCELLED");
            case "SHIPPED" -> next.equals("DELIVERED") || next.equals("RETURNED");
            case "DELIVERED" -> next.equals("RECEIVED") || next.equals("RETURNED");
            default -> false;
        };
    }
    public void initializeCheckout(String key, String fingerprint, Instant expiresAt,
                                   BigDecimal commission, int refundHours, int autoConfirmHours) {
        orderCode = "ORD-" + UUID.randomUUID();
        idempotencyKey = key;
        requestFingerprint = fingerprint;
        reservationExpiresAt = expiresAt;
        reservationStatus = "RESERVED";
        commissionRateSnapshot = commission;
        refundWindowHoursSnapshot = refundHours;
        autoConfirmHoursSnapshot = autoConfirmHours;
        paymentMethodSnapshot = "GATEWAY";
        updatedAt = Instant.now();
    }

    public boolean hasReservation() {
        return "RESERVED".equals(reservationStatus);
    }

    public boolean reservationExpired(Instant now) {
        return reservationExpiresAt != null && !reservationExpiresAt.isAfter(now);
    }

    public void consumed() {
        reservationStatus = "CONSUMED";
        stockConsumedAt = Instant.now();
    }

    public void released() {
        reservationStatus = "RELEASED";
        stockReleasedAt = Instant.now();
    }

    public void cancel(String reason) {
        cancellationReason = reason;
        transition("CANCELLED");
    }

    public void confirmReceived() {
        transition("RECEIVED");
        refundDeadlineAt = receivedAt.plusSeconds((refundWindowHoursSnapshot == null ? 72 : refundWindowHoursSnapshot) * 3600L);
    }

    public void deliveredPolicy() {
        autoConfirmDueAt = deliveredAt.plusSeconds((autoConfirmHoursSnapshot == null ? 24 : autoConfirmHoursSnapshot) * 3600L);
    }

    public void snapshotPackages(int count, BigDecimal cost, int productsWeight, int packagingWeight, int weight) {
        shippingCalculationVersion = "MANUAL_STACK_V1";
        shippingPackageCount = count;
        shippingPackagingCostVnd = cost;
        shippingProductWeightGrams = productsWeight;
        shippingPackagingWeightGrams = packagingWeight;
        shippingWeightGrams = weight;
        shippingQuotedAt = Instant.now();
        updatedAt = Instant.now();
    }
    public void markShippingQuoteVersion(String version) {
        shippingCalculationVersion = version;
    }

    public boolean isCod() {
        return "COD".equals(paymentMethodSnapshot);
    }

    public void selectCod() {
        if (!"CREATED".equals(status)) throw new IllegalStateException("Select payment method before order processing.");
        paymentMethodSnapshot = "COD";
    }
}
