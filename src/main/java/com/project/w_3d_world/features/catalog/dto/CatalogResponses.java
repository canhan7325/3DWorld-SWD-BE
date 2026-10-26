package com.project.w_3d_world.features.catalog.dto;

import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogResponses {
    private CatalogResponses() {}

    public record ProductSummary(Integer id, Integer storefrontId, String title, String category,
                                 String thumbnailUrl, BigDecimal price, BigDecimal maxPrice,
                                 BigDecimal averageRating, int reviewCount, int salesCount,
                                 String badgeType, boolean available) {
        public static ProductSummary from(Product product) {
            return new ProductSummary(product.getId(), product.getStorefrontId(), product.getTitle(),
                    product.getCategory(), null, product.getBasePrice(), product.getBasePrice(),
                    product.getAverageRating(), product.getReviewCount(), product.getSalesCount(),
                    product.getBadgeType(), false);
        }
    }

    public record VariantResponse(Integer id, String sku, String attributes, BigDecimal price,
                                  int stock, int reserved, int available, boolean active) {
        public static VariantResponse from(ProductVariant variant) {
            boolean active = variant.isActive() && "ACTIVE".equals(variant.getStatus());
            return new VariantResponse(variant.getId(), variant.getSku(), variant.getVariantAttributes(),
                    variant.getUnitPrice(), variant.getStockQuantity(), variant.getReservedQuantity(),
                    active ? Math.max(0, variant.availableQuantity()) : 0, active);
        }
    }

    public record ProductDetail(ProductSummary product, List<String> media, List<VariantResponse> variants,
                                String description, String specifications, String shippingClass,
                                StorefrontSummary storefront, boolean buyNowEligible) {}

    public record StorefrontSummary(Integer id, String name, String description, String status,
                                    boolean available) {}

    public record ReviewResponse(Integer id, int rating, String comment, String images,
                                 Instant createdAt) {}

    public record CatalogPage(List<ProductSummary> items, int page, int size, long totalElements, int totalPages) {}
}
