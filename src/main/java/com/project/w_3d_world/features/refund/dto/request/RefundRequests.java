package com.project.w_3d_world.features.refund.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class RefundRequests {
    private RefundRequests() {}
    public record Review(@NotNull Boolean approved, @NotBlank @Size(max=2000) String reason,
                         @Pattern(regexp="SELLER|PLATFORM|BUYER") String shippingPayer,
                         @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal outboundFee,
                         @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal returnFee,
                         @Size(max=255) String carrierReference) {}
    public record Transfer(@NotBlank @Size(max=255) String transferReference,
                           @NotBlank @Size(max=2000) String reason) {}
    public record ShippingAssessment(@NotBlank @Pattern(regexp="SELLER|PLATFORM|BUYER") String payer,
                                     @NotNull @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal outboundFee,
                                     @NotNull @DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal returnFee,
                                     @NotBlank @Size(max=2000) String reason,
                                     @NotBlank @Size(max=255) String carrierReference) {}
}
