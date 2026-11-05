package com.project.w_3d_world.features.order.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StatusRequest(@NotBlank String status) {}
