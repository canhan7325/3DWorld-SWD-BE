package com.project.w_3d_world.features.shipping.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record QuoteResponse(String quoteToken, long expiresAt, String version, String pricingMode,
                                BigDecimal totalShippingFee, List<ShopQuote> shops) {}
