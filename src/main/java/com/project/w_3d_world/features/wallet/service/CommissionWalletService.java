package com.project.w_3d_world.features.wallet.service;

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
public class CommissionWalletService {
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
        earning.pending(fundsReceived, order.getReceivedAt() == null ? null : order.getReceivedAt().plusSeconds(72 * 3600));
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
        // Order lock serializes retries, including returns without payment/earning (uncollected COD).
        Integer recorded = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action_type='RETURN_SHIPPING_ASSESSED' "
                + "AND entity_type='PHYSICAL_ORDER' AND entity_id=?", Integer.class, orderId);
        if (recorded != null && recorded > 0) return;
        BigDecimal debit = shippingFee.multiply(BigDecimal.TWO).setScale(2, RoundingMode.HALF_UP);
        if (debit.signum() < 0) throw new IllegalStateException("Shipping fee cannot be negative.");
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
        BigDecimal applied = wallet.getAvailableBalance().min(debit);
        if (applied.signum() > 0) {
            wallet.debitAvailable(applied);
            ledgers.save(new WalletLedger(wallet.getId(), "RETURN_SHIPPING_DEBIT", "AVAILABLE", applied.negate(),
                    wallet.getAvailableBalance(), earning.getId(), "RETURN_SHIPPING:" + orderId));
        }
        BigDecimal debt = debit.subtract(applied);
        wallet.addReturnShippingDebt(debt);
        jdbc.update("""
                INSERT INTO audit_logs(action_type,entity_type,entity_id,reason,metadata)
                VALUES ('RETURN_SHIPPING_ASSESSED','PHYSICAL_ORDER',?,'Seller bears two-way shipping on returned order',
                jsonb_build_object('total',cast(? as numeric),'debited',cast(? as numeric),'debt',cast(? as numeric)))
                """, orderId, debit, applied, debt);
    }

    @Transactional
    public void received(int orderId, Instant at) {
        earnings.lockByOrderId(orderId).ifPresent(earning -> earning.markReceived(at));
    }

    @Transactional
    public void releaseEligible(int orderId) {
        PhysicalOrder order = orders.lockById(orderId).orElseThrow();
        SellerEarning earning = earnings.lockByOrderId(orderId).orElse(null);
        if (earning == null || !"PENDING".equals(earning.getStatus()) || earning.getReleaseEligibleAt() == null
                || earning.getReleaseEligibleAt().isAfter(Instant.now()) || !"RECEIVED".equals(order.getStatus())) return;
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
            ledgers.save(new WalletLedger(wallet.getId(), "RETURN_SHIPPING_DEBT_SETTLED", "AVAILABLE", debtPaid.negate(),
                    wallet.getAvailableBalance(), earning.getId(), "RETURN_DEBT_SETTLED:" + orderId));
        }
    }

    @Transactional
    public void releaseDue() {
        earnings.findReleaseCandidates(Instant.now()).forEach(earning -> releaseEligible(earning.getOrderId()));
    }

    @Transactional(readOnly = true)
    public Wallet wallet(int userId) {
        return wallets.findByUserId(userId).orElseGet(() -> new Wallet(userId));
    }

    private Wallet lockWallet(int seller) {
        jdbc.update("INSERT INTO wallets(user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", seller);
        return wallets.lockByUserId(seller).orElseThrow();
    }
}
