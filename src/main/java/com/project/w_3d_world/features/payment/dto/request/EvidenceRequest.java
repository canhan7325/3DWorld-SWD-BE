package com.project.w_3d_world.features.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record EvidenceRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 200) String reference,
            @NotBlank @Size(max = 2000) String evidenceKey,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull @PastOrPresent Instant occurredAt) {}
