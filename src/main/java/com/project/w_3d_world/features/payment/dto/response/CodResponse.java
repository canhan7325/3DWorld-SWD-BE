package com.project.w_3d_world.features.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CodResponse(int orderId, int transactionId, String paymentMethod, String status,
                              String collectionStatus, BigDecimal expectedAmount, BigDecimal amountToCollect,
                              Instant collectedAt, Instant settledAt, String collectionReference,
                              String settlementReference) {}
