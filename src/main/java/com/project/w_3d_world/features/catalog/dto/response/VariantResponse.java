package com.project.w_3d_world.features.catalog.dto.response;

import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import java.math.BigDecimal;

public record VariantResponse(Integer id, String sku, String attributes, BigDecimal price,
                                  int stock, int reserved, int available, boolean active) {
        public static VariantResponse from(ProductVariant variant) {
            boolean active = variant.isActive() && "ACTIVE".equals(variant.getStatus());
            return new VariantResponse(variant.getId(), variant.getSku(), variant.getVariantAttributes(),
                    variant.getUnitPrice(), variant.getStockQuantity(), variant.getReservedQuantity(),
                    active ? Math.max(0, variant.availableQuantity()) : 0, active);
        }
    }
