package com.project.w_3d_world.features.shipping.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuoteRequest(@NotEmpty @Size(max = 100) List<@NotNull @Positive Integer> cartItemIds,
                               @NotNull @Positive Integer shippingAddressId) {}
