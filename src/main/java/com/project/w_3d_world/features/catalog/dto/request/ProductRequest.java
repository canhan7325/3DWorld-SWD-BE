package com.project.w_3d_world.features.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductRequest(@NotBlank @Size(max = 255) String title,
            @Size(max = 20000) String description, @NotBlank @Size(max = 255) String category,
            @Size(max = 20000) String specifications, @Size(max = 100) String shippingClass,
            @Size(max = 100) List<@Valid CreateVariantRequest> variants) {}
