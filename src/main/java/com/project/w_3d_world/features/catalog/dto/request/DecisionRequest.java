package com.project.w_3d_world.features.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DecisionRequest(@NotBlank String revision, @NotBlank @Size(max = 1000) String reason) {}
