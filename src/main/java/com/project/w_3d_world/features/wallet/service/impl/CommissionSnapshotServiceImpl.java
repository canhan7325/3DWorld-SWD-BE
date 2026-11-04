package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.wallet.entity.WalletLedger;
import com.project.w_3d_world.features.wallet.repository.SellerEarningRepository;
import com.project.w_3d_world.features.wallet.repository.WalletLedgerRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.service.CommissionSnapshotService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommissionSnapshotServiceImpl implements CommissionSnapshotService {
    private final AccountAuthorization authorization;
    private final PhysicalOrderRepository orders;
    private final SellerEarningRepository earnings;
    private final WalletRepository wallets;
    private final WalletLedgerRepository ledgers;
    private final AuditLogRepository audits;

    @Override @Transactional
    public void reconcile(int admin,int orderId) {
        authorization.requireRole(admin,RoleCode.ADMIN);
        var order=orders.lockById(orderId).orElseThrow(()->ApiException.invalidRequest("Order does not exist."));
        var earning=earnings.lockByOrderId(orderId).orElse(null);
        if(earning==null || !Set.of("PENDING","RELEASED").contains(earning.getStatus())) return;
        var rate=order.getCommissionRateSnapshot();
        // Use the historical order contract, never today's platform config or a hard-coded 5%.
        if(rate==null || rate.signum()<0 || rate.compareTo(BigDecimal.ONE)>0)
            throw new ApiException(HttpStatus.CONFLICT,"Order commission snapshot is missing or invalid.");
        var expected=order.getSubtotal().multiply(rate).setScale(2,RoundingMode.HALF_UP);
        if(earning.getGrossAmount().compareTo(order.getSubtotal())!=0)
            throw new ApiException(HttpStatus.CONFLICT,"Gross earning differs from order subtotal; manual reconciliation required.");
        var delta=expected.subtract(earning.getCommissionAmount());
        if(delta.signum()==0 && rate.compareTo(earning.getCommissionRateSnapshot())==0) return;
        var wallet=wallets.lockByUserId(earning.getSellerUserId()).orElseThrow();
        String bucket="RELEASED".equals(earning.getStatus())?"AVAILABLE":"PENDING";
        if(delta.signum()>0){
            var balance="AVAILABLE".equals(bucket)?wallet.getAvailableBalance():wallet.getPendingBalance();
            if(balance.compareTo(delta)<0) throw new ApiException(HttpStatus.CONFLICT,
                    "Commission correction requires "+delta+" VND in "+bucket+" balance. Held payout funds cannot be debited; Admin must reject the pending payout or seller must top up before retrying.");
            if("AVAILABLE".equals(bucket))wallet.debitAvailable(delta);else wallet.removePending(delta);
        } else if(delta.signum()<0){
            if("AVAILABLE".equals(bucket))wallet.creditAvailable(delta.negate());else wallet.addPending(delta.negate());
        }
        var oldRate=earning.getCommissionRateSnapshot();var oldAmount=earning.getCommissionAmount();
        earning.reconcileCommission(rate,expected);
        if(delta.signum()!=0)ledgers.save(new WalletLedger(wallet.getId(),"COMMISSION_SNAPSHOT_CORRECTION",bucket,
                delta.negate(),"AVAILABLE".equals(bucket)?wallet.getAvailableBalance():wallet.getPendingBalance(),
                earning.getId(),"COMMISSION_CORRECTION:"+orderId+":"+rate.toPlainString()+":"+expected.toPlainString()));
        audits.save(new AuditLog(admin,"COMMISSION_SNAPSHOT_RECONCILED","PHYSICAL_ORDER",orderId,
                "Reconciled earning to the historical order commission snapshot",JsonValues.write(Map.of(
                "oldRate",oldRate,"oldAmount",oldAmount,"rate",rate,"amount",expected,"delta",delta,"bucket",bucket))));
    }
}
