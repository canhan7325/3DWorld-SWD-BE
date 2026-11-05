package com.project.w_3d_world.features.shipping.dto.response;

import com.project.w_3d_world.features.shipping.ghtk.GhtkClient.Fee;
import com.project.w_3d_world.features.shipping.packing.BoxPackingService.Plan;
import java.math.BigDecimal;
import java.util.List;

public record PricedPlan(Plan packing, List<Fee> carrierFees, BigDecimal shippingFee,
                             BigDecimal packagingCost, BigDecimal combinedCost) {}
