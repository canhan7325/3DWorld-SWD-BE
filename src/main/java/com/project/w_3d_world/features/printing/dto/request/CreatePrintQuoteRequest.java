package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;

public record CreatePrintQuoteRequest(
        @NotNull @Positive Integer expectedFileVersion,
        @NotNull @Positive Integer expectedSpecVersion,
        Instant etaAt,
        @NotEmpty List<@NotNull @Valid CreatePrintQuoteItemRequest> items
) {}
