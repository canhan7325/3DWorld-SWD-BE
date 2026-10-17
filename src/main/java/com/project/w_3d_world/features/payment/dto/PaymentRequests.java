package com.project.w_3d_world.features.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class PaymentRequests {
    private PaymentRequests() {}

    public record CreatePaymentRequest(
            @NotBlank @Schema(example = "WALLET") String sourceType,
            @NotNull @Schema(description = "ID of the source business record", example = "10")
                    Integer sourceId,
            @NotBlank @Schema(example = "TOPUP") String stage,
            @DecimalMin("1000.00")
                    @Schema(
                            description = "Required only for WALLET/TOPUP",
                            example = "50000.00")
                    BigDecimal amount,
            @Schema(example = "sandbox@example.com") String description) {}
}
