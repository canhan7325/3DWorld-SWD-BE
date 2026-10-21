package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.printing.controller.PrintRequestController;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSummaryResponse;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PrintRequestControllerTest.Config.class)
@WebAppConfiguration
class PrintRequestControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired PrintRequestService service;
    @Autowired AuthSessionService sessions;
    @Autowired JwtService jwt;

    private MockMvc mvc;
    private String customerToken;

    @BeforeEach
    void setUp() {
        reset(service, sessions);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        customerToken = token("CUSTOMER");
        var summary = new PrintRequestSummaryResponse(10, "DRAFT", "Prototype",
                Instant.parse("2026-01-01T00:00:00Z"), null, null);
        when(service.getMyRequests(anyInt(), anyInt(), anyInt()))
                .thenReturn(new PagedPrintRequestResponse(List.of(summary), 0, 20, 1, 1));
        var item = new PrintRequestItemResponse(21, 4, null, "UPLOADED_FILE", "Black",
                "STANDARD", 20, new BigDecimal("12.50"), null, null, 2, null, "PENDING");
        when(service.getMyRequestById(anyInt(), anyInt()))
                .thenReturn(new PrintRequestResponse(10, "DRAFT", "Prototype",
                        Instant.parse("2026-01-01T00:00:00Z"), null, null, List.of(item)));
    }

    @Test
    void customerListUsesJwtIdentityAndApiEnvelope() throws Exception {
        var response = mvc.perform(get("/api/print/requests/getAll").header("Authorization", customerToken)
                        .param("customerUserId", "999"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(PrintRequestController.class))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].printRequestId").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.timestamp").exists()).andReturn();
        verify(service).getMyRequests(7, 0, 20);
        verify(sessions).validateSession(eq(7), any(UUID.class), any(Instant.class));
        assertFalse(response.getResponse().getContentAsString().contains("customerUserId"));
    }

    @Test
    void paginationAndEmptyListAreReturnedWithoutOtherCustomers() throws Exception {
        mvc.perform(get("/api/print/requests/getAll").param("page", "2").param("size", "10")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk());
        verify(service).getMyRequests(7, 2, 10);

        when(service.getMyRequests(7, 0, 20))
                .thenReturn(new PagedPrintRequestResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/print/requests/getAll").header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(service).getMyRequests(7, 0, 20);
    }

    @Test
    void detailReturnsSafeItemsWithoutRawKeyOrCost() throws Exception {
        var response = mvc.perform(get("/api/print/requests/getId/10")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(PrintRequestController.class))
                .andExpect(jsonPath("$.data.printRequestId").value(10))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("UPLOADED_FILE"))
                .andExpect(jsonPath("$.data.items[0].materialId").value(4))
                .andReturn();
        verify(service).getMyRequestById(7, 10);
        String body = response.getResponse().getContentAsString();
        assertFalse(body.contains("uploadedFileKey"));
        assertFalse(body.contains("estimatedUnitCost"));
        assertFalse(body.contains("customerUserId"));
    }

    @Test
    void missingAndOtherOwnerUseTheSameNonDisclosing404() throws Exception {
        when(service.getMyRequestById(7, 99))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        when(service.getMyRequestById(7, 10))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        for (int id : List.of(99, 10)) {
            mvc.perform(get("/api/print/requests/getId/{printRequestId}", id)
                            .header("Authorization", customerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(handler().handlerType(PrintRequestController.class))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Print Request was not found."));
            verify(service).getMyRequestById(7, id);
        }
    }

    @Test
    void missingInvalidAndRevokedJwtNeverReachService() throws Exception {
        mvc.perform(get("/api/print/requests/getAll"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/print/requests/getId/10")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        doThrow(ApiException.invalidSession()).when(sessions)
                .validateSession(eq(7), any(UUID.class), any(Instant.class));
        mvc.perform(get("/api/print/requests/getAll").header("Authorization", customerToken))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedNonCustomerCannotReadRequests() throws Exception {
        mvc.perform(get("/api/print/requests/getAll").header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/print/requests/getId/10").header("Authorization", token("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidPaginationAndIdsReturn400BeforeService() throws Exception {
        for (String[] parameter : new String[][]{{"page", "-1"}, {"page", "abc"},
                {"size", "0"}, {"size", "51"}, {"size", "abc"}}) {
            mvc.perform(get("/api/print/requests/getAll").param(parameter[0], parameter[1])
                            .header("Authorization", customerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        for (String id : List.of("0", "-1", "abc", "999999999999999999999")) {
            mvc.perform(get("/api/print/requests/getId/{printRequestId}", id)
                            .header("Authorization", customerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void genericGetAliasesAreNotMapped() throws Exception {
        mvc.perform(get("/api/print/requests").header("Authorization", customerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/print/requests/10").header("Authorization", customerToken))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    private String token(String role) {
        return "Bearer " + jwt.generateToken(7, Set.of(role), "", "unit@example.invalid")
                .getTokenValue();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PrintRequestController.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean PrintRequestService printRequestService() { return mock(PrintRequestService.class); }
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
        @Bean JwtService jwt() {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000);
        }
        @Override
        public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper()));
        }
    }
}
