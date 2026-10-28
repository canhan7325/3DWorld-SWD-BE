package com.project.w_3d_world.features.wallet.service;

import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import java.math.BigDecimal;
import java.time.Instant;

public interface CommissionWalletService {
    BigDecimal MINIMUM_RESERVE = new BigDecimal("100000");
    void postPending(int orderId, Instant fundsReceived);
    void returned(int orderId, BigDecimal shippingFee);
    void assessShipping(int orderId, Long refundId, int actor, String payer, BigDecimal outbound, BigDecimal inbound, String reason, String carrierReference);
    void topUpVerified(PaymentTransaction transaction);
    void recordDebtPayments(int seller, BigDecimal amount, String key);
    boolean hasReserve(int user);
    void requireReserve(int user);
    void requireNoActiveRefund(int orderId);
    void requireNoShippingDebt(int user);
    void received(int orderId, Instant at);
    void releaseEligible(int orderId);
    void releaseDue();
    Wallet wallet(int userId);
}
