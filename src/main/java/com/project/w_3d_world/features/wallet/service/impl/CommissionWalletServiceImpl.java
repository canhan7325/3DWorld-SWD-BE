package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.PlatformConfigService;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.entity.SellerEarning;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import com.project.w_3d_world.features.wallet.entity.WalletLedger;
import com.project.w_3d_world.features.wallet.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommissionWalletServiceImpl implements CommissionWalletService {
    public static final BigDecimal RATE = new BigDecimal("0.05");
    private final SellerEarningRepository earnings;
    private final WalletRepository wallets;
    private final JdbcTemplate jdbc;
    private final WalletLedgerRepository ledgers;
    private final PhysicalOrderRepository orders;
    private final StorefrontRepository stores;
    private final PlatformConfigService platformConfig;

    @Transactional
    public void postPending(int orderId, Instant fundsReceived) {
        PhysicalOrder order = orders.lockById(orderId).orElseThrow();
        if (!Set.of("PAID", "PROCESSING", "SHIPPED", "DELIVERED", "RECEIVED").contains(order.getStatus())) return;
        if (earnings.findByOrderId(orderId).isPresent()) return;
        int seller = stores.findById(order.getStorefrontId()).orElseThrow().getOwnerUserId();
        Wallet wallet = lockWallet(seller);
        BigDecimal gross = order.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = order.getCommissionRateSnapshot() == null ? RATE : order.getCommissionRateSnapshot();
        BigDecimal commission = gross.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(commission);
        SellerEarning earning = earnings.saveAndFlush(new SellerEarning(orderId, seller, wallet.getId(),
                gross, rate, commission, net));
        earning.pending(fundsReceived, order.getReceivedAt() == null ? null : (order.getRefundDeadlineAt() == null ? order.getReceivedAt().plusSeconds(72 * 3600) : order.getRefundDeadlineAt()));
        wallet.addPending(net);
        if (net.signum() > 0) ledgers.save(new WalletLedger(wallet.getId(), "SELLER_REVENUE_PENDING", "PENDING",
                net, wallet.getPendingBalance(), earning.getId(), "EARNING_PENDING:" + orderId));
    }

    @Transactional
    public void returned(int orderId, BigDecimal shippingFee) {
        PhysicalOrder order = orders.lockById(orderId).orElseThrow();
        if (!"RETURNED".equals(order.getStatus())) throw new IllegalStateException("A returned order is required.");
        int seller = stores.findById(order.getStorefrontId()).orElseThrow().getOwnerUserId();
        Wallet wallet = lockWallet(seller);
        // Revenue reversal and actual carrier fee assessment are independent.

        SellerEarning earning = earnings.lockByOrderId(orderId).orElseGet(() -> {
            SellerEarning source = new SellerEarning(orderId, seller, wallet.getId(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            source.voidForReturn();
            return earnings.saveAndFlush(source);
        });
        // A returned sale must not release its pending revenue later. Refund payout is separate.
        if ("PENDING".equals(earning.getStatus()) || "HELD".equals(earning.getStatus())) {
            BigDecimal pending = earning.getNetAmount();
            wallet.removePending(pending);
            if (pending.signum() > 0) ledgers.save(new WalletLedger(wallet.getId(), "RETURN_REVENUE_REVERSED", "PENDING",
                    pending.negate(), wallet.getPendingBalance(), earning.getId(), "RETURN_REVENUE:" + orderId));
            earning.voidForReturn();
        }
    }

    /** One assessment per order, linked to the return/refund and actual carrier reconciliation. */
    @Transactional
    public void assessShipping(int orderId, Long refundId, int actor, String payer, BigDecimal outbound,
                               BigDecimal inbound, String reason, String carrierReference) {
        PhysicalOrder order = orders.lockById(orderId).orElseThrow();
        int seller = stores.findById(order.getStorefrontId()).orElseThrow().getOwnerUserId();
        Wallet wallet = lockWallet(seller);
        if (!Set.of("SELLER", "PLATFORM", "BUYER").contains(payer)
                || outbound.signum() < 0 || inbound.signum() < 0) {
            throw com.project.w_3d_world.common.exception.ApiException.invalidRequest("Invalid shipping assessment.");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action_type='RETURN_SHIPPING_ASSESSED' AND entity_type='PHYSICAL_ORDER' AND entity_id=?", Integer.class, orderId) > 0)
            throw new com.project.w_3d_world.common.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT, "Shipping already assessed for this order.");
        BigDecimal total = outbound.add(inbound);
        BigDecimal applied = "SELLER".equals(payer) ? wallet.getAvailableBalance().min(total) : BigDecimal.ZERO;
        BigDecimal debt = "SELLER".equals(payer) ? total.subtract(applied) : BigDecimal.ZERO;
        String meta = String.format(java.util.Locale.ROOT, "{\"refundId\":%s,\"payer\":\"%s\",\"outboundFee\":%s,\"returnFee\":%s,\"debited\":%s,\"debt\":%s,\"carrierReference\":\"%s\"}", refundId == null ? "null" : refundId.toString(), payer, outbound, inbound, applied, debt, carrierReference.replace("\"", ""));
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action_type,entity_type,entity_id,reason,metadata) VALUES (?,?,?,?,?,cast(? as jsonb))", actor, "RETURN_SHIPPING_ASSESSED", "PHYSICAL_ORDER", orderId, reason, meta);
        if (applied.signum() > 0) {
            wallet.debitAvailable(applied);
            ledgers.save(new WalletLedger(wallet.getId(), "RETURN_SHIPPING_DEBIT", "AVAILABLE", applied.negate(),
                    wallet.getAvailableBalance(), null, "SHIPPING_ASSESSMENT:" + orderId));
        }
        wallet.addReturnShippingDebt(debt);
    }

    @Transactional
    public void topUpVerified(com.project.w_3d_world.features.payment.entity.PaymentTransaction transaction) {
        Wallet wallet = wallets.lockByUserId(transaction.getPayerUserId()).orElseThrow();
        if (!wallet.getId().equals(transaction.getWalletId()) || !"SUCCESS".equals(transaction.getStatus())
                || !"TOPUP".equals(transaction.getTransactionType()) || transaction.getAmount().signum() <= 0) {
            throw com.project.w_3d_world.common.exception.ApiException.invalidRequest("Verified wallet top-up required.");
        }
        String key = "TOPUP:" + transaction.getId();
        if (jdbc.queryForObject("SELECT count(*) FROM wallet_ledgers WHERE idempotency_key=?", Integer.class, key) > 0) return;
        wallet.creditAvailable(transaction.getAmount());
        ledgers.saveAndFlush(new WalletLedger(wallet.getId(), "TOPUP", "AVAILABLE", transaction.getAmount(),
                wallet.getAvailableBalance(), transaction.getId(), null, key));
        BigDecimal paid = wallet.settleReturnShippingDebt(wallet.getAvailableBalance());
        if (paid.signum() > 0) {
            wallet.debitAvailable(paid);
            ledgers.save(new WalletLedger(wallet.getId(), "RETURN_SHIPPING_DEBT_SETTLED", "AVAILABLE", paid.negate(),
                    wallet.getAvailableBalance(), transaction.getId(), null, "TOPUP_DEBT:" + transaction.getId()));
            recordDebtPayments(wallet.getUserId(), paid, "TOPUP:" + transaction.getId());
        }
        transaction.fulfillment("COMPLETED");
    }

    public void recordDebtPayments(int seller, BigDecimal amount, String key) {
        // The existing wallet return_shipping_debt field is the source of truth; ledger entries already record settlement.
    }

    @Transactional
    public boolean hasReserve(int user) {
        var wallet = lockWallet(user);
        return wallet.getAvailableBalance().compareTo(MINIMUM_RESERVE) >= 0 && wallet.getReturnShippingDebt().signum() == 0;
    }

    @Transactional
    public void requireReserve(int user) {
        if (!hasReserve(user)) throw new com.project.w_3d_world.common.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                "Seller registration requires at least 100000 VND available and no shipping debt.");
    }

    public void requireNoActiveRefund(int orderId) {
        if (jdbc.queryForObject("SELECT count(*) FROM transactions WHERE physical_order_id=? AND transaction_type='REFUND' AND status IN ('PENDING','PROCESSING','SUCCESS')", Integer.class, orderId) > 0)
            throw new com.project.w_3d_world.common.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT, "Order has an active or paid refund.");
    }

    @Transactional
    public void requireNoShippingDebt(int user) {
        if (lockWallet(user).getReturnShippingDebt().signum() > 0) throw new com.project.w_3d_world.common.exception.ApiException(
                org.springframework.http.HttpStatus.CONFLICT, "Shipping debt must be settled before creating a shipment.");
    }

    @Transactional
    public void received(int orderId, Instant at) {
        var order = orders.lockById(orderId).orElseThrow();
        earnings.lockByOrderId(orderId).ifPresent(earning -> earning.markReceived(order.getRefundDeadlineAt() == null ? at.plusSeconds(72 * 3600) : order.getRefundDeadlineAt()));
    }

    @Transactional
    public void releaseEligible(int orderId) {
        PhysicalOrder order = orders.lockById(orderId).orElseThrow();
        SellerEarning earning = earnings.lockByOrderId(orderId).orElse(null);
        if (earning == null || !"PENDING".equals(earning.getStatus()) || earning.getReleaseEligibleAt() == null
                || earning.getReleaseEligibleAt().isAfter(Instant.now()) || !"RECEIVED".equals(order.getStatus())) return;
        Integer openRefunds = jdbc.queryForObject("SELECT count(*) FROM transactions WHERE physical_order_id=? AND transaction_type='REFUND' AND status IN ('PENDING','PROCESSING','SUCCESS')", Integer.class, orderId);
        if (openRefunds != null && openRefunds > 0) return;
        if (order.getRefundDeadlineAt() != null && !order.getRefundDeadlineAt().isBefore(Instant.now())) return;
        Wallet wallet = lockWallet(earning.getSellerUserId());
        BigDecimal net = earning.getNetAmount();
        wallet.releasePending(net);
        earning.release();
        if (net.signum() > 0) {
            ledgers.save(new WalletLedger(wallet.getId(), "SELLER_REVENUE_RELEASE", "PENDING", net.negate(),
                    wallet.getPendingBalance(), earning.getId(), "EARNING_RELEASE_PENDING:" + orderId));
            ledgers.save(new WalletLedger(wallet.getId(), "SELLER_REVENUE_RELEASE", "AVAILABLE", net,
                    wallet.getAvailableBalance(), earning.getId(), "EARNING_RELEASE:" + orderId));
        }
        // Collect only from newly available money; payout holds and pending funds are never spent.
        BigDecimal debtPaid = wallet.settleReturnShippingDebt(wallet.getAvailableBalance());
        if (debtPaid.signum() > 0) {
            wallet.debitAvailable(debtPaid);
            recordDebtPayments(earning.getSellerUserId(), debtPaid, "RELEASE:" + orderId);
            ledgers.save(new WalletLedger(wallet.getId(), "RETURN_SHIPPING_DEBT_SETTLED", "AVAILABLE", debtPaid.negate(),
                    wallet.getAvailableBalance(), earning.getId(), "RETURN_DEBT_SETTLED:" + orderId));
        }
    }

    @Transactional
    public void releaseDue() {
        earnings.findReleaseCandidates(Instant.now()).forEach(earning -> releaseEligible(earning.getOrderId()));
    }

    @Transactional
    public Wallet wallet(int userId) {
        return lockWallet(userId);
    }

    private Wallet lockWallet(int seller) {
        jdbc.update("INSERT INTO wallets(user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", seller);
        return wallets.lockByUserId(seller).orElseThrow();
    }
}
