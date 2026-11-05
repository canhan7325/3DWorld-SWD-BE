package com.project.w_3d_world.features.storefront.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateRequest(@Size(min = 1, max = 255) String name, @Size(max = 10000) String description) {}
