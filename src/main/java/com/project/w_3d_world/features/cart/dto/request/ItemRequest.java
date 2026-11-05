package com.project.w_3d_world.features.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemRequest(@NotNull Integer variantId, @Min(1) int quantity) {}
