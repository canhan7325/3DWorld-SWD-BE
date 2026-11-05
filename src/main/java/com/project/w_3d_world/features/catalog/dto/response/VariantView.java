package com.project.w_3d_world.features.catalog.dto.response;

import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import java.math.BigDecimal;

public record VariantView(int id, String sku, String attributes, BigDecimal price, int stock, int reserved,
                              int available, boolean active, boolean fragile, int lowStockThreshold,
                              String packingGroup, Integer weightGrams, BigDecimal lengthCm,
                              BigDecimal widthCm, BigDecimal heightCm) {
        public static VariantView from(ProductVariant v) {
            return new VariantView(v.getId(), v.getSku(), v.getVariantAttributes(), v.getUnitPrice(),
                    v.getStockQuantity(), v.getReservedQuantity(), v.availableQuantity(), v.isActive(), v.isFragile(),
                    v.getLowStockThreshold(), v.getPackingGroup(), v.getProductWeightGrams(),
                    v.getProductLengthCm(), v.getProductWidthCm(), v.getProductHeightCm());
        }
    }
