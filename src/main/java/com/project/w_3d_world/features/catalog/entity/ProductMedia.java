package com.project.w_3d_world.features.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Integer id;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "media_type", length = 30)
    private String mediaType;

    @Column(name = "media_url", nullable = false, columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "variant_id")
    private Integer variantId;

    public ProductMedia(int productId, Integer variantId, String url, int sortOrder) {
        this.productId = productId;
        this.variantId = variantId;
        this.mediaUrl = url;
        this.mediaType = "IMAGE";
        this.sortOrder = sortOrder;
    }

}
