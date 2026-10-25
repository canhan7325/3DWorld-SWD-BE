package com.project.w_3d_world.features.shipping.dto;

import com.project.w_3d_world.features.shipping.ghtk.GhtkClient.Fee;
import com.project.w_3d_world.features.shipping.packing.BoxPackingService.Plan;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class ShippingQuoteDtos {
    private ShippingQuoteDtos() {}
    public record QuoteRequest(@NotEmpty @Size(max = 100) List<@NotNull @Positive Integer> cartItemIds,
                               @NotNull @Positive Integer shippingAddressId) {}
    public record PricedPlan(Plan packing, List<Fee> carrierFees, BigDecimal shippingFee,
                             BigDecimal packagingCost, BigDecimal combinedCost) {}
    public record ShopQuote(int storefrontId, PricedPlan selected, List<PricedPlan> alternatives) {}
    public record QuoteResponse(String quoteToken, long expiresAt, String version, String pricingMode,
                                BigDecimal totalShippingFee, List<ShopQuote> shops) {}
    public record SignedQuote(int userId, String fingerprint, long expiresAt, String version, List<ShopQuote> shops) {}
}
