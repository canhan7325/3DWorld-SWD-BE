package com.project.w_3d_world.features.payment.dto.response;

import java.math.BigDecimal;

public record PaymentResponse(
            Integer transactionId,
            Integer attemptId,
            String appTransId,
            BigDecimal amount,
            String status,
            String orderUrl,
            String zpTransToken) {}
