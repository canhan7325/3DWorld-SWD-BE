package com.project.w_3d_world.features.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PayoutRequest(@NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal amount,@NotNull @Positive Integer bankAccountId,@NotBlank @Size(max=1000) String reason){}
