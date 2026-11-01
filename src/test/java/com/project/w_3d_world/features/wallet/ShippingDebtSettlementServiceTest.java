package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.entity.SellerEarning;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import com.project.w_3d_world.features.wallet.repository.SellerEarningRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.repository.WalletLedgerRepository;
import com.project.w_3d_world.features.wallet.service.PlatformConfigService;
import com.project.w_3d_world.features.wallet.service.impl.CommissionWalletServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShippingDebtSettlementServiceTest {
    @Test void releasedSaleSettlesDebtBeforeAvailableFundsAndReplaysAreHarmless() {
        var earnings=mock(SellerEarningRepository.class);var wallets=mock(WalletRepository.class);
        var jdbc=mock(JdbcTemplate.class);var ledgers=mock(WalletLedgerRepository.class);var orders=mock(PhysicalOrderRepository.class);
        var service=new CommissionWalletServiceImpl(earnings,wallets,jdbc,ledgers,orders,mock(StorefrontRepository.class),mock(PlatformConfigService.class));
        var wallet=new Wallet(9);ReflectionTestUtils.setField(wallet,"id",7);
        wallet.addReturnShippingDebt(new BigDecimal("80000"));wallet.addPending(new BigDecimal("95000"));
        var earning=new SellerEarning(1,9,7,new BigDecimal("100000"),new BigDecimal("0.05"),new BigDecimal("5000"),new BigDecimal("95000"));
        ReflectionTestUtils.setField(earning,"id",3);earning.pending(Instant.now(),Instant.now().minusSeconds(1));
        var order=new PhysicalOrder(1,2,"{}",UUID.randomUUID(),new BigDecimal("100000"),BigDecimal.ZERO);
        ReflectionTestUtils.setField(order,"status","RECEIVED");ReflectionTestUtils.setField(order,"receivedAt",Instant.now().minusSeconds(4*86400L));ReflectionTestUtils.setField(order,"refundDeadlineAt",Instant.now().minusSeconds(1));
        when(orders.lockById(1)).thenReturn(Optional.of(order));when(earnings.lockByOrderId(1)).thenReturn(Optional.of(earning));
        when(wallets.lockByUserId(9)).thenReturn(Optional.of(wallet));
        when(earnings.hasFullPlatformReceipt(eq(1),any())).thenReturn(true);
        service.releaseEligible(1);
        assertEquals(0,wallet.getReturnShippingDebt().signum());assertEquals(new BigDecimal("15000"),wallet.getAvailableBalance());
        assertEquals("RELEASED",earning.getStatus());assertEquals(0,wallet.getPendingBalance().signum());
        service.releaseEligible(1);assertEquals(new BigDecimal("15000"),wallet.getAvailableBalance());
        verify(ledgers,times(3)).save(any());
    }
    @Test void topUpPaysDebtAndDuplicateCallbackDoesNotCreditAgain() {
        var wallets=mock(WalletRepository.class);var jdbc=mock(JdbcTemplate.class);var ledgers=mock(WalletLedgerRepository.class);
        var service=new CommissionWalletServiceImpl(mock(SellerEarningRepository.class),wallets,jdbc,ledgers,mock(PhysicalOrderRepository.class),mock(StorefrontRepository.class),mock(PlatformConfigService.class));
        var wallet=new Wallet(9);ReflectionTestUtils.setField(wallet,"id",7);wallet.addReturnShippingDebt(new BigDecimal("130000"));
        var tx=new PaymentTransaction("WALLET","topup",9,"TOPUP",new BigDecimal("100000"));tx.setSource("WALLET",7);tx.markSuccess();ReflectionTestUtils.setField(tx,"id",11);
        when(wallets.lockByUserId(9)).thenReturn(Optional.of(wallet));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("TOPUP:11"))).thenReturn(0,1);
        service.topUpVerified(tx);service.topUpVerified(tx);
        assertEquals(new BigDecimal("30000"),wallet.getReturnShippingDebt());assertEquals(0,wallet.getAvailableBalance().signum());
        verify(ledgers,times(1)).saveAndFlush(any());
    }
}
