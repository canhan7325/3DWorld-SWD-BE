package com.project.w_3d_world.features.catalog.dto.response;

import java.util.List;

public record ProductDetail(ProductSummary product, List<String> media, List<VariantResponse> variants,
                                String description, String specifications, String shippingClass,
                                StorefrontSummary storefront, boolean buyNowEligible) {}
