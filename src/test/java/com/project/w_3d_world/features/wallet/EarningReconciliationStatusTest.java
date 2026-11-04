package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.wallet.repository.EarningReleaseStatusRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.impl.EarningReconciliationServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarningReconciliationStatusTest {
    @Test void explainsWhyPast72HoursAloneIsNotEnough() {
        var repo=mock(EarningReleaseStatusRepository.class);var commission=mock(CommissionWalletService.class);
        var service=new EarningReconciliationServiceImpl(repo,commission,mock(AccountAuthorization.class),mock(com.project.w_3d_world.features.wallet.service.CommissionSnapshotService.class));
        var row=eligible();row.put("platform_received",BigDecimal.ZERO);row.put("blocking_refund",true);
        when(repo.status(37)).thenReturn(List.of(row));
        var result=service.status(1,37);
        assertEquals(false,result.get("eligibleForRelease"));
        assertEquals(List.of("PLATFORM_RECEIPT_NOT_RECONCILED","REFUND_IN_PROGRESS_OR_PAID"),result.get("blockers"));
        verifyNoInteractions(commission);
    }
    @Test void retryReportsCommittedRecognitionDateAndDoesNotInventIt() {
        var repo=mock(EarningReleaseStatusRepository.class);var commission=mock(CommissionWalletService.class);
        var service=new EarningReconciliationServiceImpl(repo,commission,mock(AccountAuthorization.class),mock(com.project.w_3d_world.features.wallet.service.CommissionSnapshotService.class));
        var pending=eligible();var released=eligible();released.put("earning_status","RELEASED");
        released.put("released_at",Timestamp.from(Instant.now()));
        when(repo.status(37)).thenReturn(List.of(pending),List.of(released));
        var result=service.reconcile(1,37);
        verify(commission).releaseEligible(37);
        assertEquals("RECOGNIZED",result.get("recognitionStatus"));assertNotNull(result.get("released_at"));
    }
    @Test void releasedZeroCommissionAgainstFivePercentOrderIsFlagged(){
        var repo=mock(EarningReleaseStatusRepository.class);
        var service=new EarningReconciliationServiceImpl(repo,mock(CommissionWalletService.class),mock(AccountAuthorization.class),mock(com.project.w_3d_world.features.wallet.service.CommissionSnapshotService.class));
        var row=eligible();row.put("earning_status","RELEASED");row.put("released_at",Timestamp.from(Instant.now()));
        row.put("subtotal",new BigDecimal("400000"));row.put("order_commission_rate",new BigDecimal("0.05"));row.put("commission_amount",BigDecimal.ZERO);
        when(repo.status(31)).thenReturn(List.of(row));
        var response=service.status(1,31);
        assertEquals(new BigDecimal("20000.00"),response.get("expected_commission"));
        assertEquals(true,response.get("commissionMismatch"));
        assertEquals("COMMISSION_RECONCILIATION_REQUIRED",response.get("recognitionStatus"));
        assertEquals(List.of("COMMISSION_SNAPSHOT_MISMATCH"),response.get("blockers"));
    }
    private Map<String,Object> eligible(){
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("earning_id",4);row.put("earning_status","PENDING");row.put("order_status","RECEIVED");
        row.put("received_at",Timestamp.from(Instant.now().minusSeconds(4*86400L)));
        row.put("platform_funds_received_at",Timestamp.from(Instant.now().minusSeconds(5*86400L)));
        row.put("effective_refund_deadline",Timestamp.from(Instant.now().minusSeconds(86400)));
        row.put("total_amount",new BigDecimal("100000"));row.put("platform_received",new BigDecimal("100000"));
        row.put("net_amount",new BigDecimal("95000"));row.put("pending_balance",new BigDecimal("95000"));row.put("blocking_refund",false);
        return row;
    }
}
