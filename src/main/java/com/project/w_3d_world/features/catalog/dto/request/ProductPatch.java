package com.project.w_3d_world.features.catalog.dto.request;

import jakarta.validation.constraints.Size;

public record ProductPatch(@Size(min = 1, max = 255) String title,
            @Size(max = 20000) String description, @Size(min = 1, max = 255) String category,
            @Size(max = 20000) String specifications, @Size(max = 100) String shippingClass) {}
