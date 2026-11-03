package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.wallet.controller.WalletHistoryController;
import com.project.w_3d_world.features.wallet.repository.WalletHistoryRepository;
import com.project.w_3d_world.features.wallet.service.WalletHistoryService;
import com.project.w_3d_world.features.wallet.service.impl.WalletHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WalletHistoryAuthorizationTest {
    @Test void controllerRequiresSellerAndUsesAuthenticatedIdentity() {
        var history=mock(WalletHistoryService.class);
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(ControllerRoleAuthorizationTest.Security.class);
            context.registerBean(WalletHistoryService.class,()->history);
            context.registerBean(WalletHistoryController.class);
            context.refresh();
            var controller=context.getBean(WalletHistoryController.class);
            var user=mock(AuthenticatedUser.class);when(user.userId()).thenReturn(9);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
            assertThrows(AccessDeniedException.class,()->controller.payouts(user,0,20));
            assertThrows(AccessDeniedException.class,()->controller.ledgers(user,0,20));verifyNoInteractions(history);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller","",List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
            controller.payouts(user,1,10);controller.ledgers(user,0,20);
            verify(history).payouts(9,1,10);verify(history).ledgers(9,0,20);
        } finally {SecurityContextHolder.clearContext();}
    }
    @Test void serviceRejectsInvalidPaginationBeforeQueries() {
        var repo=mock(WalletHistoryRepository.class);var auth=mock(AccountAuthorization.class);
        var service=new WalletHistoryServiceImpl(repo,auth);
        assertThrows(ApiException.class,()->service.payouts(9,-1,20));
        assertThrows(ApiException.class,()->service.ledgers(9,0,101));
        assertThrows(ApiException.class,()->service.ledgers(9,0,0));verifyNoInteractions(repo);
    }
}
