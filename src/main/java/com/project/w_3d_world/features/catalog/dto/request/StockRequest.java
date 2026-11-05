package com.project.w_3d_world.features.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockRequest(@NotNull @Min(0) Integer stock, @NotBlank @Size(max = 1000) String reason) {}
