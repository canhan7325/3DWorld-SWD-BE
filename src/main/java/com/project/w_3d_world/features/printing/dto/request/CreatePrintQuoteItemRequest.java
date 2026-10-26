package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePrintQuoteItemRequest(
        @NotNull @Positive Integer printRequestItemId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal materialCostSnapshot,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal laborCostSnapshot,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal optionFeeSnapshot
) {}
