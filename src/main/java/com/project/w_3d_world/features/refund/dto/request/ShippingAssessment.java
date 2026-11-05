package com.project.w_3d_world.features.refund.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ShippingAssessment(@NotBlank @Pattern(regexp="SELLER|PLATFORM|BUYER") String payer,
                                     @NotNull @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal outboundFee,
                                     @NotNull @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal returnFee,
                                     @NotBlank @Size(max=2000) String reason,
                                     @NotBlank @Size(max=255) String carrierReference) {}
