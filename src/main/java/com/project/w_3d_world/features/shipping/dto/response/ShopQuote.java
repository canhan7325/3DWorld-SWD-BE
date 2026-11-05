package com.project.w_3d_world.features.shipping.dto.response;

import java.util.List;

public record ShopQuote(int storefrontId, PricedPlan selected, List<PricedPlan> alternatives) {}
