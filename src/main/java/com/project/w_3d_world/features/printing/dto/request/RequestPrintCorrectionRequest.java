package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RequestPrintCorrectionRequest(
        @NotNull @Positive Integer expectedFileVersion,
        @NotNull @Positive Integer expectedSpecVersion,
        @NotBlank String reason) {}
