package com.project.w_3d_world.features.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PodPaymentStatusResponse(
            Integer transactionId,
            Integer printRequestId,
            Integer quoteId,
            String financialStatus,
            String fulfillmentStatus,
            BigDecimal amount,
            String currency,
            Integer latestAttemptId,
            String latestAttemptStatus,
            boolean attemptReconciliationPending,
            Instant attemptExpiresAt,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {}
