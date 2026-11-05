package com.project.w_3d_world.features.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record VariantPatch(@Size(min = 1, max = 100) String sku, Map<String, Object> attributes,
            @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal price, Boolean active,
            Boolean fragile, @Min(0) Integer lowStockThreshold, @Size(max = 50) String packingGroup,
            @Positive Integer weightGrams, @DecimalMin("0.01") BigDecimal lengthCm,
            @DecimalMin("0.01") BigDecimal widthCm, @DecimalMin("0.01") BigDecimal heightCm) {}
