package com.project.w_3d_world.features.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class CodDtos {
    private CodDtos() {}

    public record EvidenceRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 200) String reference,
            @NotBlank @Size(max = 2000) String evidenceKey,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull @PastOrPresent Instant occurredAt) {}

    public record CodResponse(int orderId, int transactionId, String paymentMethod, String status,
                              String collectionStatus, BigDecimal expectedAmount, BigDecimal amountToCollect,
                              Instant collectedAt, Instant settledAt, String collectionReference,
                              String settlementReference) {}
}
