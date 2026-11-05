package com.project.w_3d_world.features.shipping.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemRequest(@NotNull @Positive Integer orderItemId, @Min(1) int quantity) {}
