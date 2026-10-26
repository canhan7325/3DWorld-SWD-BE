package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelPrintRequestRequest(@NotBlank String reason) {}
