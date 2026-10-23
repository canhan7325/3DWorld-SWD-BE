package com.project.w_3d_world.features.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Integer id;

    @Column(name = "cart_id", nullable = false)
    private Integer cartId;

    @Column(name = "variant_id", nullable = false)
    private Integer variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "price_at_added", precision = 18, scale = 2)
    private BigDecimal priceAtAdded;

    @Column(name = "price_acknowledged_at")
    private Instant priceAcknowledgedAt;

    public CartItem(Integer cartId, Integer variantId, int quantity) {
        this.cartId = cartId;
        this.variantId = variantId;
        this.quantity = quantity;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }
    public void acknowledgePrice(BigDecimal price) {
        priceAtAdded = price;
        priceAcknowledgedAt = Instant.now();
        updatedAt = Instant.now();
    }
}
