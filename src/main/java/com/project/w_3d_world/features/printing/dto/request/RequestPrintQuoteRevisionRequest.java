package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RequestPrintQuoteRevisionRequest(
        @NotNull @Positive Integer expectedFileVersion,
        @NotNull @Positive Integer expectedSpecVersion,
        @NotBlank @Size(max = 10_000) String reason
) {}
