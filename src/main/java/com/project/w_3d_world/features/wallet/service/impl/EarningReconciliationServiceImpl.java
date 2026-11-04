package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.wallet.repository.EarningReleaseStatusRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.CommissionSnapshotService;
import com.project.w_3d_world.features.wallet.service.EarningReconciliationService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EarningReconciliationServiceImpl implements EarningReconciliationService {
    private final EarningReleaseStatusRepository repository;
    private final CommissionWalletService commission;
    private final AccountAuthorization authorization;
    private final CommissionSnapshotService snapshots;

    @Override @Transactional(readOnly=true)
    public Map<String,Object> status(int admin,int orderId){
        authorization.requireRole(admin,RoleCode.ADMIN);
        var rows=repository.status(orderId);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"Order does not exist.");
        var row=new LinkedHashMap<>(rows.getFirst());
        var blockers=new ArrayList<String>();
        boolean released="RELEASED".equals(row.get("earning_status"));
        if(!released){
            if(row.get("earning_id")==null)blockers.add("EARNING_NOT_CREATED");
            else if(!"PENDING".equals(row.get("earning_status")))blockers.add("EARNING_NOT_PENDING");
            if(!"RECEIVED".equals(row.get("order_status"))||row.get("received_at")==null)blockers.add("ORDER_NOT_RECEIVED");
            if(row.get("platform_funds_received_at")==null)blockers.add("FUNDS_RECEIPT_NOT_POSTED");
            BigDecimal total=(BigDecimal)row.get("total_amount");
            if(total==null||total.signum()<=0||total.compareTo((BigDecimal)row.get("platform_received"))!=0)blockers.add("PLATFORM_RECEIPT_NOT_RECONCILED");
            var deadline=(Timestamp)row.get("effective_refund_deadline");
            if(deadline==null||!deadline.toInstant().isBefore(Instant.now()))blockers.add("REFUND_WINDOW_NOT_EXPIRED");
            if(Boolean.TRUE.equals(row.get("blocking_refund")))blockers.add("REFUND_IN_PROGRESS_OR_PAID");
            if(row.get("net_amount")!=null && (row.get("pending_balance")==null
                    ||((BigDecimal)row.get("pending_balance")).compareTo((BigDecimal)row.get("net_amount"))<0))blockers.add("INSUFFICIENT_PENDING_BALANCE");
        }else if(row.get("released_at")==null)blockers.add("RELEASE_TIMESTAMP_MISSING");
        if(row.get("order_commission_rate")!=null && row.get("commission_amount")!=null){
            BigDecimal expected=((BigDecimal)row.get("subtotal")).multiply((BigDecimal)row.get("order_commission_rate")).setScale(2,java.math.RoundingMode.HALF_UP);
            row.put("expected_commission",expected);
            boolean mismatch=expected.compareTo((BigDecimal)row.get("commission_amount"))!=0;
            row.put("commissionMismatch",mismatch);
            if(mismatch)blockers.add("COMMISSION_SNAPSHOT_MISMATCH");
        }
        row.put("blockers",blockers);
        row.put("eligibleForRelease",!released&&blockers.isEmpty());
        row.put("recognitionStatus",released&&row.get("released_at")!=null
                ?(Boolean.TRUE.equals(row.get("commissionMismatch"))?"COMMISSION_RECONCILIATION_REQUIRED":"RECOGNIZED")
                :"NOT_RECOGNIZED");
        return row;
    }
    @Override @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Map<String,Object> reconcile(int admin,int orderId){
        authorization.requireRole(admin,RoleCode.ADMIN);
        // The proxied service owns its transaction. Re-read only after a successful commit.
        status(admin,orderId);
        snapshots.reconcile(admin,orderId);
        commission.releaseEligible(orderId);
        return status(admin,orderId);
    }
}
