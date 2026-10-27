package com.project.w_3d_world.features.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class PlatformConfigDtos {
    private PlatformConfigDtos() {}
    public record UpdateRequest(@NotNull @Digits(integer=1,fraction=5) @Schema(example="0.05") BigDecimal commissionRate,
                                @NotNull @Positive @Digits(integer=16,fraction=2) @Schema(example="99000") BigDecimal storefrontMonthlyFee) {}
}
