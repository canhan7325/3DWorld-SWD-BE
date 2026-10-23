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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_attributes", columnDefinition = "jsonb")
    private String variantAttributes;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "fragile", nullable = false)
    private boolean fragile;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @Column(name = "packing_group", length = 50)
    private String packingGroup;

    @Column(name = "product_weight_grams")
    private Integer productWeightGrams;

    @Column(name = "product_length_cm", precision = 10, scale = 2)
    private BigDecimal productLengthCm;

    @Column(name = "product_width_cm", precision = 10, scale = 2)
    private BigDecimal productWidthCm;

    @Column(name = "product_height_cm", precision = 10, scale = 2)
    private BigDecimal productHeightCm;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "ACTIVE";

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
        if (quantity < 1 || quantity > reservedQuantity) {
            throw new IllegalStateException("Reserved stock does not match the order.");
        }
        reservedQuantity -= quantity;
    }

    public void consume(int quantity) {
        if (quantity < 1 || quantity > reservedQuantity) {
            throw new IllegalArgumentException("Invalid reserved stock quantity.");
        }
        stockQuantity -= quantity;
        reservedQuantity -= quantity;
    }
}
