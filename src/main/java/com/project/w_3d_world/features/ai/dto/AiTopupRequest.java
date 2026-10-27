package com.project.w_3d_world.features.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AiTopupRequest(
        @NotNull(message = "Amount is required.")
        @Min(value = 1, message = "Amount must be at least 1.")
        @Max(value = 5000, message = "Amount cannot exceed 5000 credits per request.")
        Integer amount
) {}
