package com.project.w_3d_world.features.wallet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.entity.*;
import com.project.w_3d_world.features.wallet.repository.*;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class CommissionWalletTests {
    PhysicalOrderRepository orders=mock(PhysicalOrderRepository.class); StorefrontRepository stores=mock(StorefrontRepository.class); WalletRepository wallets=mock(WalletRepository.class); SellerEarningRepository earnings=mock(SellerEarningRepository.class); WalletLedgerRepository ledgers=mock(WalletLedgerRepository.class); CommissionWalletService service=new CommissionWalletService(earnings,wallets,mock(org.springframework.jdbc.core.JdbcTemplate.class),ledgers,orders,stores);
    PhysicalOrder order;
    @BeforeEach void setup(){order=new PhysicalOrder(1,2,"{}",null,new BigDecimal("500000"),new BigDecimal("30000")); ReflectionTestUtils.setField(order,"id",10); ReflectionTestUtils.setField(order,"status","RECEIVED"); when(orders.lockById(10)).thenReturn(Optional.of(order)); Storefront store=new Storefront(7,"Shop",null); ReflectionTestUtils.setField(store,"id",2); when(stores.findById(2)).thenReturn(Optional.of(store)); when(earnings.findByOrderId(10)).thenReturn(Optional.empty()); when(wallets.lockByUserId(7)).thenReturn(Optional.of(new Wallet(7))); when(wallets.saveAndFlush(any())).thenAnswer(x->{Wallet w=x.getArgument(0); if(w.getId()==null) ReflectionTestUtils.setField(w,"id",3); return w;}); when(earnings.saveAndFlush(any())).thenAnswer(x->x.getArgument(0));}
    @Test void commissionIsFivePercentOfProductSubtotalOnly(){service.postPending(10,java.time.Instant.now()); var cap=org.mockito.ArgumentCaptor.forClass(SellerEarning.class); verify(earnings).saveAndFlush(cap.capture()); var e=cap.getValue(); assertEquals(new BigDecimal("500000.00"),e.getGrossAmount()); assertEquals(new BigDecimal("25000.00"),e.getCommissionAmount()); assertEquals(new BigDecimal("475000.00"),e.getNetAmount()); assertEquals(new BigDecimal("0.05"),e.getCommissionRateSnapshot()); verify(ledgers).save(any());}
    @Test void duplicateOrderDoesNotPostAgain(){when(earnings.findByOrderId(10)).thenReturn(Optional.of(mock(SellerEarning.class))); service.postPending(10,java.time.Instant.now()); verify(earnings,never()).saveAndFlush(any()); verify(ledgers,never()).save(any());}
    @Test void availableReleaseIsAtomicAndIdempotent(){Wallet wallet=new Wallet(7); ReflectionTestUtils.setField(wallet,"id",3); ReflectionTestUtils.setField(wallet,"pendingBalance",new BigDecimal("475000")); ReflectionTestUtils.setField(wallet,"id",3); SellerEarning earning=new SellerEarning(10,7,3,new BigDecimal("500000"),new BigDecimal("0.05"),new BigDecimal("25000"),new BigDecimal("475000")); earning.pending(java.time.Instant.now(),java.time.Instant.now().minusSeconds(1)); when(earnings.lockByOrderId(10)).thenReturn(Optional.of(earning)); when(orders.lockById(10)).thenReturn(Optional.of(order)); when(wallets.lockByUserId(7)).thenReturn(Optional.of(wallet)); service.releaseEligible(10); assertEquals(new BigDecimal("475000"),wallet.getAvailableBalance()); verify(ledgers).save(any()); service.releaseEligible(10); verify(ledgers,times(1)).save(any());}
}
