package com.project.w_3d_world.features.shipping.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EventRequest(@NotBlank String event) {}
