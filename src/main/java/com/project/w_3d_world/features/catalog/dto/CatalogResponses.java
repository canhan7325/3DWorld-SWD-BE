package com.project.w_3d_world.features.catalog.dto;

import com.project.w_3d_world.features.catalog.entity.Product;
import com.project.w_3d_world.features.catalog.entity.ProductMedia;
import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import java.math.BigDecimal;
import java.util.List;

public final class CatalogResponses {
    private CatalogResponses() {}

    public record ProductSummary(Integer id, Integer storefrontId, String title, String category) {
        public static ProductSummary from(Product product) {
            return new ProductSummary(
                    product.getId(), product.getStorefrontId(), product.getTitle(), product.getCategory());
        }
    }

    public record VariantResponse(Integer id, String sku, String attributes, BigDecimal price,
                                  int stock, int reserved, int available, boolean active) {
        public static VariantResponse from(ProductVariant variant) {
            return new VariantResponse(variant.getId(), variant.getSku(), variant.getVariantAttributes(),
                    variant.getUnitPrice(), variant.getStockQuantity(), variant.getReservedQuantity(),
                    Math.max(0, variant.getStockQuantity() - variant.getReservedQuantity()), variant.isActive());
        }
    }

    public record ProductDetail(ProductSummary product, List<String> media, List<VariantResponse> variants) {}

    public record CatalogPage(List<ProductSummary> items, int page, int size, long totalElements, int totalPages) {}
}
