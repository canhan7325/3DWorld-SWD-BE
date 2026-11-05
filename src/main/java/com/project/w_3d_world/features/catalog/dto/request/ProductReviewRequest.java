package com.project.w_3d_world.features.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductReviewRequest(@NotNull @Positive Integer orderItemId,
        @Min(1) @Max(5) int rating,@Size(max=5000) String comment) {}
