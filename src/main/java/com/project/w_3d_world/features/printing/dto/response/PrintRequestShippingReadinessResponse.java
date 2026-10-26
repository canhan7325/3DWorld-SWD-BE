package com.project.w_3d_world.features.printing.dto.response;

import java.util.List;

public record PrintRequestShippingReadinessResponse(
        boolean shippingReady,
        List<String> shippingBlockers,
        int completedItems,
        int totalItems,
        boolean shipmentExists) {}
