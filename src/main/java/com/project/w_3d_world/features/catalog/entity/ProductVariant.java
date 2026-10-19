package com.project.w_3d_world.features.catalog.entity;

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
@Table(name = "product_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Integer id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "variant_attributes", columnDefinition = "text")
    private String variantAttributes;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public int availableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    public void reserve(int quantity) {
        if (quantity < 1 || quantity > availableQuantity()) {
            throw new IllegalArgumentException("Insufficient available stock.");
        }
        reservedQuantity += quantity;
    }

    public void release(int quantity) {
        reservedQuantity = Math.max(0, reservedQuantity - quantity);
    }

    public void consume(int quantity) {
        if (quantity < 1 || quantity > reservedQuantity) {
            throw new IllegalArgumentException("Invalid reserved stock quantity.");
        }
        stockQuantity -= quantity;
        reservedQuantity -= quantity;
    }
}
