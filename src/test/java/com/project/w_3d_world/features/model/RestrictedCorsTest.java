package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.util.Base64;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RestrictedCorsTest.Config.class)
@WebAppConfiguration
class RestrictedCorsTest {
    @Autowired WebApplicationContext context;

    @Test
    void originNotConfiguredOutsideDevelopmentIsDenied() throws Exception {
        MockMvc mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(options("/api/models/uploads")
                        .header("Origin", "https://unapproved.example.invalid")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void productionAllowsOnlyExplicitlyConfiguredOrigins() {
        var config = new SecurityConfig();
        var environment = new MockEnvironment()
                .withProperty("APP_CORS_ALLOWED_ORIGINS", "https://approved.example.invalid");
        var cors = config.corsConfigurationSource(environment)
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/models/uploads"));
        assertNotNull(cors);
        assertEquals("https://approved.example.invalid", cors.checkOrigin("https://approved.example.invalid"));
        assertNull(cors.checkOrigin("https://unapproved.example.invalid"));
        assertEquals(java.util.List.of("Content-Disposition", "Retry-After"), cors.getExposedHeaders());
        assertFalse(cors.getAllowCredentials());
        assertThrows(IllegalStateException.class, () -> config.corsConfigurationSource(
                new MockEnvironment().withProperty("APP_CORS_ALLOWED_ORIGINS", "*")));
    }

    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class Config {
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
        @Bean JwtService jwt() {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000);
        }
    }
}
