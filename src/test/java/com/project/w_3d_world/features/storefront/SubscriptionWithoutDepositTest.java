package com.project.w_3d_world.features.storefront;

import com.project.w_3d_world.features.storefront.service.StorefrontService;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.storefront.repository.StorefrontSubscriptionRepository;
import com.project.w_3d_world.features.storefront.entity.Storefront;
import com.project.w_3d_world.features.storefront.entity.StorefrontSubscription;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import com.project.w_3d_world.features.auth.service.SellerVerificationService;
import com.project.w_3d_world.features.auth.repository.RoleRepository;
import com.project.w_3d_world.features.auth.repository.UserRoleRepository;
import com.project.w_3d_world.features.auth.entity.Role;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.wallet.service.PlatformConfigService;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubscriptionWithoutDepositTest {
    @Test void newSellerCanBuyAndActivateSubscriptionWithoutAnyWalletOrDeposit(){
        var stores=mock(StorefrontRepository.class);var subscriptions=mock(StorefrontSubscriptionRepository.class);
        var users=mock(UserRepository.class);var verification=mock(SellerVerificationService.class);
        var roles=mock(RoleRepository.class);var grants=mock(UserRoleRepository.class);var config=mock(PlatformConfigService.class);
        var service=new StorefrontService(stores,subscriptions,users,verification,roles,grants,mock(AuditLogRepository.class),mock(JdbcTemplate.class),mock(EntityManager.class),config);
        var user=new User("seller@test.com","hash","Seller",null);ReflectionTestUtils.setField(user,"id",9);
        var store=new Storefront(9,"Shop",null);ReflectionTestUtils.setField(store,"id",2);
        var fee=new BigDecimal("99000");var subscription=new StorefrontSubscription(2,fee);ReflectionTestUtils.setField(subscription,"id",4);
        when(users.lockById(9)).thenReturn(Optional.of(user));when(verification.eligible(user)).thenReturn(true);
        when(stores.findByOwnerUserId(9)).thenReturn(Optional.of(store));when(stores.lockById(2)).thenReturn(Optional.of(store));when(stores.findById(2)).thenReturn(Optional.of(store));
        when(subscriptions.findFirstByStorefrontIdAndStatusOrderByIdDesc(2,"PENDING_PAYMENT")).thenReturn(Optional.empty());
        when(config.decimal(anyString(),any())).thenReturn(fee);when(subscriptions.saveAndFlush(any())).thenReturn(subscription);
        assertEquals("PENDING_PAYMENT",service.subscribe(9).status());
        when(subscriptions.findById(4)).thenReturn(Optional.of(subscription));
        var role=mock(Role.class);when(role.getId()).thenReturn(3);when(roles.findByCode(RoleCode.SELLER)).thenReturn(Optional.of(role));
        var transaction=new PaymentTransaction("SUBSCRIPTION","SUB:4",9,"SUBSCRIPTION",fee);
        transaction.setSource("SUBSCRIPTION",4);transaction.markSuccess();
        service.activateVerifiedPayment(transaction);
        assertEquals("COMPLETED",transaction.getFulfillmentStatus());assertNotNull(subscription.getPaidAt());
        assertEquals("ACTIVE",store.getStatus().name());verify(grants).save(any());
        // Replayed provider callback cannot extend the subscription or grant the role twice.
        var end=subscription.getEndAt();service.activateVerifiedPayment(transaction);
        assertEquals(end,subscription.getEndAt());verify(grants,times(1)).save(any());
    }
}
