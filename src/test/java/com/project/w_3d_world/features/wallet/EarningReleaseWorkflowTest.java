package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.entity.SellerEarning;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import com.project.w_3d_world.features.wallet.repository.SellerEarningRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.repository.WalletLedgerRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.PlatformConfigService;
import com.project.w_3d_world.features.wallet.service.impl.CommissionWalletServiceImpl;
import com.project.w_3d_world.features.wallet.service.impl.EarningReleaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EarningReleaseWorkflowTest {
    private final SellerEarningRepository earnings=mock(SellerEarningRepository.class);
    private final WalletRepository wallets=mock(WalletRepository.class);
    private final WalletLedgerRepository ledgers=mock(WalletLedgerRepository.class);
    private final PhysicalOrderRepository orders=mock(PhysicalOrderRepository.class);
    private final CommissionWalletServiceImpl service=new CommissionWalletServiceImpl(earnings,wallets,mock(JdbcTemplate.class),ledgers,orders,mock(StorefrontRepository.class),mock(PlatformConfigService.class));
    private PhysicalOrder order;
    private SellerEarning earning;
    private Wallet wallet;
    @BeforeEach void fixtures(){
        order=new PhysicalOrder(1,2,"{}",UUID.randomUUID(),new BigDecimal("100000"),BigDecimal.ZERO);
        ReflectionTestUtils.setField(order,"status","RECEIVED");
        ReflectionTestUtils.setField(order,"receivedAt",Instant.now().minusSeconds(4*86400L));
        ReflectionTestUtils.setField(order,"refundDeadlineAt",Instant.now().minusSeconds(86400));
        earning=new SellerEarning(1,9,7,new BigDecimal("100000"),new BigDecimal("0.05"),new BigDecimal("5000"),new BigDecimal("95000"));
        ReflectionTestUtils.setField(earning,"id",3);
        earning.pending(Instant.now().minusSeconds(5*86400L),null);
        wallet=new Wallet(9);ReflectionTestUtils.setField(wallet,"id",7);wallet.addPending(new BigDecimal("95000"));
        when(orders.lockById(1)).thenReturn(Optional.of(order));when(earnings.lockByOrderId(1)).thenReturn(Optional.of(earning));
        when(wallets.lockByUserId(9)).thenReturn(Optional.of(wallet));when(earnings.hasFullPlatformReceipt(eq(1),any())).thenReturn(true);
    }
    @Test void overdueReceiptRepairsNullEligibilityAndSetsReleasedAtOnce(){
        service.releaseEligible(1);
        assertEquals("RELEASED",earning.getStatus());assertNotNull(earning.getReleasedAt());
        assertEquals(new BigDecimal("5000"),earning.getCommissionAmount());
        assertEquals(new BigDecimal("95000"),wallet.reserveBalance(new BigDecimal("100000")));
        assertEquals(new BigDecimal("5000"),wallet.reserveShortfall(new BigDecimal("100000")));
        assertEquals(0,wallet.withdrawableBalance(new BigDecimal("100000")).signum());
        assertEquals(order.getRefundDeadlineAt(),earning.getReleaseEligibleAt());
        var released=earning.getReleasedAt();service.releaseEligible(1);
        assertEquals(released,earning.getReleasedAt());assertEquals(new BigDecimal("95000"),wallet.getAvailableBalance());
        verify(ledgers,times(2)).save(any());
    }
    @Test void staleFutureEarningDateDoesNotBlockAnExpiredOrderWindow(){
        ReflectionTestUtils.setField(earning,"releaseEligibleAt",Instant.now().plusSeconds(86400));
        service.releaseEligible(1);assertEquals("RELEASED",earning.getStatus());
    }
    @Test void futureOrderDeadlineBlocksEvenIfEarningDateWasManuallyBackdated(){
        ReflectionTestUtils.setField(order,"refundDeadlineAt",Instant.now().plusSeconds(86400));
        ReflectionTestUtils.setField(earning,"releaseEligibleAt",Instant.now().minusSeconds(86400));
        service.releaseEligible(1);assertPending();
    }
    @Test void unreceivedUnfundedAndActiveRefundOrdersStayPending(){
        ReflectionTestUtils.setField(order,"status","DELIVERED");service.releaseEligible(1);assertPending();
        ReflectionTestUtils.setField(order,"status","RECEIVED");ReflectionTestUtils.setField(earning,"fundsReceivedAt",null);
        service.releaseEligible(1);assertPending();
        ReflectionTestUtils.setField(earning,"fundsReceivedAt",Instant.now());when(earnings.hasFullPlatformReceipt(eq(1),any())).thenReturn(false);
        service.releaseEligible(1);assertPending();
        when(earnings.hasFullPlatformReceipt(eq(1),any())).thenReturn(true);when(earnings.hasBlockingRefund(1)).thenReturn(true);
        service.releaseEligible(1);assertPending();
    }
    @Test void missingOrderDeadlineUsesReceiptPlusRefundSnapshot(){
        ReflectionTestUtils.setField(order,"refundDeadlineAt",null);
        service.releaseEligible(1);assertEquals("RELEASED",earning.getStatus());
        assertEquals(order.getReceivedAt().plusSeconds(72*3600),earning.getReleaseEligibleAt());
    }
    @Test void oneFailedEarningDoesNotStopOtherOrdersAndWillBeRetried(){
        var commission=mock(CommissionWalletService.class);
        when(earnings.findDueOrderIds(any())).thenReturn(List.of(1,2));
        doThrow(new IllegalStateException("broken wallet")).when(commission).releaseEligible(1);
        var batch=new EarningReleaseServiceImpl(earnings,commission);
        assertDoesNotThrow(batch::releaseDue);assertDoesNotThrow(batch::releaseDue);
        verify(commission,times(2)).releaseEligible(1);verify(commission,times(2)).releaseEligible(2);
    }
    private void assertPending(){assertEquals("PENDING",earning.getStatus());assertNull(earning.getReleasedAt());assertEquals(0,wallet.getAvailableBalance().signum());verifyNoInteractions(ledgers);}
}
