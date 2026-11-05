package com.project.w_3d_world.features.storefront.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequest(@NotBlank @Size(max = 255) String name, @Size(max = 10000) String description) {}
