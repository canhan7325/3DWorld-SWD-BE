package com.project.w_3d_world.features.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "physical_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "physical_order_item_id") private Integer id;
    @Column(name = "physical_order_id", nullable = false) private Integer orderId;
    @Column(name = "variant_id", nullable = false) private Integer variantId;
    @Column(name = "sku_snapshot", nullable = false) private String skuSnapshot;
    @Column(name = "variant_snapshot", columnDefinition = "text") private String variantSnapshot;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price_snapshot", nullable = false, precision = 18, scale = 2) private BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 18, scale = 2) private BigDecimal lineTotal;

    public PhysicalOrderItem(Integer orderId, Integer variantId, String skuSnapshot,
                             String variantSnapshot, int quantity, BigDecimal unitPrice) {
        this.orderId = orderId;
        this.variantId = variantId;
        this.skuSnapshot = skuSnapshot;
        this.variantSnapshot = variantSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
