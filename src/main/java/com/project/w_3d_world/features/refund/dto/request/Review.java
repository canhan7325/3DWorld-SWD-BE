package com.project.w_3d_world.features.refund.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record Review(@NotNull Boolean approved, @NotBlank @Size(max=2000) String reason,
                         @Pattern(regexp="SELLER|PLATFORM|BUYER") String shippingPayer,
                         @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal outboundFee,
                         @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal returnFee,
                         @Size(max=255) String carrierReference) {}
