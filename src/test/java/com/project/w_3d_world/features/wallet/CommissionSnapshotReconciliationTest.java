package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.wallet.entity.SellerEarning;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import com.project.w_3d_world.features.wallet.repository.SellerEarningRepository;
import com.project.w_3d_world.features.wallet.repository.WalletLedgerRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.service.impl.CommissionSnapshotServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommissionSnapshotReconciliationTest {
    private final PhysicalOrderRepository orders=mock(PhysicalOrderRepository.class);
    private final SellerEarningRepository earnings=mock(SellerEarningRepository.class);
    private final WalletRepository wallets=mock(WalletRepository.class);
    private final WalletLedgerRepository ledgers=mock(WalletLedgerRepository.class);
    private final AuditLogRepository audits=mock(AuditLogRepository.class);
    private final CommissionSnapshotServiceImpl service=new CommissionSnapshotServiceImpl(mock(AccountAuthorization.class),orders,earnings,wallets,ledgers,audits);
    private Wallet wallet;
    private SellerEarning earning;
    private PhysicalOrder order;
    @BeforeEach void fixture(){
        order=new PhysicalOrder(1,2,"{}",UUID.randomUUID(),new BigDecimal("400000"),new BigDecimal("48400"));
        ReflectionTestUtils.setField(order,"commissionRateSnapshot",new BigDecimal("0.05"));
        earning=new SellerEarning(31,9,7,new BigDecimal("400000"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("400000"));
        ReflectionTestUtils.setField(earning,"id",21);earning.pending(java.time.Instant.now(),null);
        wallet=new Wallet(9);ReflectionTestUtils.setField(wallet,"id",7);
        when(orders.lockById(31)).thenReturn(Optional.of(order));when(earnings.lockByOrderId(31)).thenReturn(Optional.of(earning));when(wallets.lockByUserId(9)).thenReturn(Optional.of(wallet));
    }
    @Test void fixesReleasedZeroRateAgainstOrderFivePercentExactlyOnce(){
        earning.release();var at=earning.getReleasedAt();wallet.creditAvailable(new BigDecimal("400000"));
        service.reconcile(1,31);service.reconcile(1,31);
        money("20000",earning.getCommissionAmount());money("380000",earning.getNetAmount());money("380000",wallet.getAvailableBalance());
        assertEquals(at,earning.getReleasedAt());assertEquals("RELEASED",earning.getStatus());
        verify(ledgers,times(1)).save(any());verify(audits,times(1)).save(any());
    }
    @Test void correctsPendingNetBeforeRelease(){
        wallet.addPending(new BigDecimal("400000"));service.reconcile(1,31);
        money("20000",earning.getCommissionAmount());money("380000",wallet.getPendingBalance());assertEquals("PENDING",earning.getStatus());
    }
    @Test void heldPayoutFundsCannotBeSilentlySpentAndNoEarningMutationOccurs(){
        earning.release();wallet.creditAvailable(new BigDecimal("400000"));wallet.hold(new BigDecimal("400000"));
        var error=assertThrows(ApiException.class,()->service.reconcile(1,31));
        assertTrue(error.getMessage().contains("Held payout"));money("0",earning.getCommissionAmount());money("400000",wallet.getHeldBalance());verifyNoInteractions(ledgers,audits);
    }
    @Test void historicalZeroRateIsNotOverwrittenWithCurrentFivePercent(){
        ReflectionTestUtils.setField(order,"commissionRateSnapshot",BigDecimal.ZERO);
        service.reconcile(1,31);money("0",earning.getCommissionAmount());verifyNoInteractions(ledgers,audits);
    }
    private void money(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
}
