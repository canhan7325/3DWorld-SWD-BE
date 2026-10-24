package com.project.w_3d_world.features.catalog.dto;

import com.project.w_3d_world.features.catalog.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SellerProductDtos {
    private SellerProductDtos() {}

    public record ProductRequest(@NotBlank @Size(max = 255) String title,
            @Size(max = 20000) String description, @NotBlank @Size(max = 255) String category,
            @Size(max = 20000) String specifications, @Size(max = 100) String shippingClass,
            @Size(max = 100) List<@Valid CreateVariantRequest> variants) {}

    public record ProductPatch(@Size(min = 1, max = 255) String title,
            @Size(max = 20000) String description, @Size(min = 1, max = 255) String category,
            @Size(max = 20000) String specifications, @Size(max = 100) String shippingClass) {}

    public record CreateVariantRequest(@NotBlank @Size(max = 100) String sku,
            Map<String, Object> attributes, @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stock, Boolean active, Boolean fragile, @Min(0) Integer lowStockThreshold,
            @Size(max = 50) String packingGroup, @Positive Integer weightGrams,
            @DecimalMin("0.01") BigDecimal lengthCm, @DecimalMin("0.01") BigDecimal widthCm,
            @DecimalMin("0.01") BigDecimal heightCm) {}

    public record VariantPatch(@Size(min = 1, max = 100) String sku, Map<String, Object> attributes,
            @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal price, Boolean active,
            Boolean fragile, @Min(0) Integer lowStockThreshold, @Size(max = 50) String packingGroup,
            @Positive Integer weightGrams, @DecimalMin("0.01") BigDecimal lengthCm,
            @DecimalMin("0.01") BigDecimal widthCm, @DecimalMin("0.01") BigDecimal heightCm) {}

    public record StockRequest(@NotNull @Min(0) Integer stock, @NotBlank @Size(max = 1000) String reason) {}
    public record DecisionRequest(@NotBlank String revision, @NotBlank @Size(max = 1000) String reason) {}
    public record MediaResponse(int id, Integer variantId, String url, int sortOrder) {
        public static MediaResponse from(ProductMedia media) {
            return new MediaResponse(media.getId(), media.getVariantId(), media.getMediaUrl(), media.getSortOrder());
        }
    }
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
    public record ProductView(int id, int storefrontId, String title, String description, String category,
                              String specifications, String shippingClass, String status, String moderationReason,
                              String revision, Instant updatedAt, List<MediaResponse> media, List<VariantView> variants) {}
}
