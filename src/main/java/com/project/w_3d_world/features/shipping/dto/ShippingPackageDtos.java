package com.project.w_3d_world.features.shipping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class ShippingPackageDtos {
    private ShippingPackageDtos() {}

    public record ItemRequest(@NotNull @Positive Integer orderItemId, @Min(1) int quantity) {}
    public record PackageRequest(@NotNull @Positive Integer packagingProfileId,
                                 @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items) {}
    public record SaveRequest(@NotEmpty @Size(max = 100) List<@Valid PackageRequest> packages) {}
    public record ItemResponse(int orderItemId, int variantId, int quantity) {}
    public record PackageResponse(int id, String code, int index, String profileCode, int productWeightGrams,
                                  int packagingWeightGrams, int packageWeightGrams, BigDecimal packagingCost,
                                  List<ItemResponse> items) {}
    public record ProfileResponse(int id, String code, String name, BigDecimal lengthCm, BigDecimal widthCm,
                                  BigDecimal heightCm, int maxProductWeightGrams, boolean supportsFragile,
                                  String allowedPackingGroups) {}
}
