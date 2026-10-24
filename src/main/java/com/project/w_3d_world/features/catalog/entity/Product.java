package com.project.w_3d_world.features.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Integer id;

    @Column(name = "storefront_id", nullable = false)
    private Integer storefrontId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String category;

    @Column(columnDefinition = "text")
    private String specifications;

    @Column(name = "shipping_class", length = 100)
    private String shippingClass;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "moderation_reason", columnDefinition = "text")
    private String moderationReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "created_by_user_id")
    private Integer createdByUserId;

    @Column(name = "base_price", precision = 18, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "average_rating", precision = 3, scale = 2, nullable = false)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "badge_type", length = 30)
    private String badgeType;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "sales_count", nullable = false)
    private int salesCount;

    public Product(int storefrontId, int creatorId, String title) {
        this.storefrontId = storefrontId;
        this.createdByUserId = creatorId;
        this.title = title;
    }

    public void edit(String title, String description, String category, String specifications,
                     String shippingClass) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (category != null) {
            this.category = category;
        }
        if (specifications != null) {
            this.specifications = specifications;
        }
        if (shippingClass != null) {
            this.shippingClass = shippingClass;
        }
        touch();
    }

    public void touch() {
        Instant now = Instant.now();
        updatedAt = updatedAt == null || now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public void changeStatus(String status, String reason) {
        this.status = status;
        this.moderationReason = reason;
        if ("PUBLISHED".equals(status)) {
            publishedAt = Instant.now();
        }
        touch();
    }

}
