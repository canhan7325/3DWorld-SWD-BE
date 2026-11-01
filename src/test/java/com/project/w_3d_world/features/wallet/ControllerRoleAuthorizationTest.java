package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.features.wallet.controller.AdminFinanceController;
import com.project.w_3d_world.features.wallet.service.AdminFinanceService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ControllerRoleAuthorizationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Security {}

    @Test void financeControllerRejectsSellerBeforeServiceAndAllowsAdmin() {
        var service = mock(AdminFinanceService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Security.class);
            context.registerBean(AdminFinanceService.class, () -> service);
            context.registerBean(AdminFinanceController.class);
            context.refresh();
            var controller = context.getBean(AdminFinanceController.class);
            var user = mock(AuthenticatedUser.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "seller", "", List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
            assertThrows(AccessDeniedException.class, () -> controller.cash(user));
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            controller.cash(user);
            verify(service).cash(user.userId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
