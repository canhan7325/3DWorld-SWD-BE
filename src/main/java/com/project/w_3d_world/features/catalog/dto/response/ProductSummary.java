package com.project.w_3d_world.features.catalog.dto.response;

import com.project.w_3d_world.features.catalog.entity.Product;
import java.math.BigDecimal;

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
