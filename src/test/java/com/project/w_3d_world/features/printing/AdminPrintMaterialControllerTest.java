package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.printing.controller.AdminPrintMaterialController;
import com.project.w_3d_world.features.printing.dto.response.AdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.service.AdminPrintMaterialService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.*;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminPrintMaterialControllerTest.Config.class)
@WebAppConfiguration
class AdminPrintMaterialControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired AdminPrintMaterialService service;
    @Autowired AuthSessionService sessions;
    @Autowired JwtService jwt;
    private MockMvc mvc;
    private String adminToken;
    private String customerToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        reset(service, sessions);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = token("ADMIN"); customerToken = token("CUSTOMER"); staffToken = token("PRINT_STAFF");
        var data = new PagedAdminPrintMaterialResponse(
                List.of(new AdminPrintMaterialResponse(1, "PLA", "FDM", "GRAM", new BigDecimal("350.00"), true)),
                0, 20, 1, 1);
        when(service.getMaterials(any(), any(), anyInt(), anyInt())).thenReturn(data);
    }

    @Test
    void adminCanListAllFiltersAndCostIsVisible() throws Exception {
        mvc.perform(get("/api/admin/print/materials").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].materialId").value(1))
                .andExpect(jsonPath("$.data.content[0].estimatedUnitCost").value(350.00))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        verify(service).getMaterials(null, null, 0, 20);

        mvc.perform(get("/api/admin/print/materials")
                        .param("technologyCode", "FDM").param("isActive", "false")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        verify(service).getMaterials("FDM", false, 1, 10);
    }

    @Test
    void emptyResultIs200() throws Exception {
        when(service.getMaterials(null, null, 0, 20))
                .thenReturn(new PagedAdminPrintMaterialResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/admin/print/materials").header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void invalidQueryParametersReturn400BeforeService() throws Exception {
        for (String[][] values : List.of(
                new String[][]{{"page", "-1"}}, new String[][]{{"size", "0"}},
                new String[][]{{"size", "51"}}, new String[][]{{"page", "abc"}},
                new String[][]{{"isActive", "unknown"}}, new String[][]{{"isActive", ""}},
                new String[][]{{"technologyCode", " "}}, new String[][]{{"technologyCode", "FDM "}})) {
            var request = get("/api/admin/print/materials").header("Authorization", adminToken);
            for (String[] value : values) request = request.param(value[0], value[1]);
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingInvalidOrRevokedSessionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/print/materials")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/print/materials").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        doThrow(com.project.w_3d_world.common.exception.ApiException.invalidSession())
                .when(sessions).validateSession(eq(7), any(UUID.class), any(Instant.class));
        mvc.perform(get("/api/admin/print/materials").header("Authorization", adminToken))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void nonAdminRolesAreForbidden() throws Exception {
        for (String token : List.of(customerToken, staffToken, token("SELLER"), token("DESIGNER"))) {
            mvc.perform(get("/api/admin/print/materials").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminWithMultipleRolesIsAllowedAndNullFieldsSerializeAsNull() throws Exception {
        when(service.getMaterials(null, null, 0, 20)).thenReturn(
                new PagedAdminPrintMaterialResponse(List.of(
                        new AdminPrintMaterialResponse(2, "PETG", "FDM", null, null, false)), 0, 20, 1, 1));
        mvc.perform(get("/api/admin/print/materials").header("Authorization", token("ADMIN", "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].isActive").value(false))
                .andExpect(jsonPath("$.data.content[0].estimatedUnitCost")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    private String token(String... roles) {
        return "Bearer " + jwt.generateToken(7, Set.of(roles), "", "admin-test@example.invalid").getTokenValue();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AdminPrintMaterialController.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean AdminPrintMaterialService materialService() { return mock(AdminPrintMaterialService.class); }
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
        @Bean JwtService jwt() {
            byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
            return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000);
        }
        @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper()));
        }
    }
}
