package com.project.w_3d_world.features.refund.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Transfer(@NotBlank @Size(max=255) String transferReference,
                           @NotBlank @Size(max=2000) String reason) {}
