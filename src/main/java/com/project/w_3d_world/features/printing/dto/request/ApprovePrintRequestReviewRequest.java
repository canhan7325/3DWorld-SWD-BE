package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApprovePrintRequestReviewRequest(
        @NotNull @Positive Integer expectedFileVersion,
        @NotNull @Positive Integer expectedSpecVersion) {}
