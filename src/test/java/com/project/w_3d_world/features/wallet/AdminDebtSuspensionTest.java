package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.enums.StorefrontStatus;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.entity.Wallet;
import com.project.w_3d_world.features.wallet.repository.AdminDashboardRepository;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.wallet.service.impl.AdminDashboardServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminDebtSuspensionTest {
    @Test void onlyOverdueInactiveShopsCanBeSuspendedAndDebtMustBeClearedToRestore() {
        var stores=mock(StorefrontRepository.class);var wallets=mock(WalletRepository.class);
        var orders=mock(PhysicalOrderRepository.class);var audits=mock(AuditLogRepository.class);
        var service=new AdminDashboardServiceImpl(mock(AdminDashboardRepository.class),stores,wallets,audits,orders);
        var shop=new Storefront(9,"Shop",null);shop.activateUntil(Instant.now().plusSeconds(86400));
        var wallet=new Wallet(9);wallet.addReturnShippingDebt(new BigDecimal("80000"));
        when(stores.lockById(2)).thenReturn(Optional.of(shop));when(wallets.lockByUserId(9)).thenReturn(Optional.of(wallet));
        assertThrows(ApiException.class,()->service.suspend(1,2,30,"Overdue debt"));
        ReflectionTestUtils.setField(wallet,"shippingDebtSince",Instant.now().minusSeconds(40*86400L));
        when(orders.existsRecentShopOrder(eq(2),any())).thenReturn(true);
        assertThrows(ApiException.class,()->service.suspend(1,2,30,"Overdue debt"));
        when(orders.existsRecentShopOrder(eq(2),any())).thenReturn(false);
        service.suspend(1,2,30,"Overdue debt, no recent sales");
        assertEquals(StorefrontStatus.SUSPENDED,shop.getStatus());assertFalse(shop.canPublish());
        assertThrows(ApiException.class,()->service.restore(1,2,"Paid"));
        wallet.settleReturnShippingDebt(new BigDecimal("80000"));
        when(audits.existsByActionTypeAndEntityTypeAndEntityId("SHOP_SUSPENDED_FOR_DEBT","STOREFRONT",2)).thenReturn(true);
        service.restore(1,2,"Debt cleared");assertEquals(StorefrontStatus.ACTIVE,shop.getStatus());
        verify(audits,times(2)).save(any());
    }
}
