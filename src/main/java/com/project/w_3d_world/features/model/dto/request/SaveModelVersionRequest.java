package com.project.w_3d_world.features.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SaveModelVersionRequest(
        @NotNull @Positive Integer baseVersionId,
        @Size(max = 10_000) String changeNote) {}
