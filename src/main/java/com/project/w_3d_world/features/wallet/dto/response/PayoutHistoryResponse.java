package com.project.w_3d_world.features.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PayoutHistoryResponse(int transactionId, BigDecimal amount, String status,
                                    Integer bankAccountId, String bankName, String accountNumberMasked,
                                    String accountHolder, String transferReference, Instant createdAt,
                                    String reason) {}
