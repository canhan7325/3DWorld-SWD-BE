package com.project.w_3d_world.features.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletLedgerResponse(int ledgerId, String entryType, String balanceBucket,
                                   BigDecimal signedAmount, BigDecimal balanceAfter,
                                   Integer transactionId, Integer earningId, Integer orderId,
                                   Instant createdAt) {}
