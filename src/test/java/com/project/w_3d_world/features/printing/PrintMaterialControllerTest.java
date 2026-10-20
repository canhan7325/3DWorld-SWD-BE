package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.printing.controller.PrintMaterialController;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintMaterialResponse;
import com.project.w_3d_world.features.printing.service.PrintMaterialService;

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

import java.security.SecureRandom;
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
@ContextConfiguration(classes = PrintMaterialControllerTest.Config.class)
@WebAppConfiguration
class PrintMaterialControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired PrintMaterialService service;
    @Autowired AuthSessionService sessions;
    @Autowired JwtService jwt;

    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() {
        reset(service, sessions);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        token = "Bearer " + jwt.generateToken(7, Set.of("CUSTOMER"), "", "unit@example.invalid")
                .getTokenValue();
        var material = new PrintMaterialResponse(4, "PLA", "FDM", "GRAM");
        when(service.getActiveMaterials(any(), anyInt(), anyInt()))
                .thenReturn(new PagedPrintMaterialResponse(List.of(material), 0, 20, 1, 1));
        when(service.getActiveMaterialById(anyInt())).thenReturn(material);
    }

    @Test
    void authenticatedCatalogUsesApiEnvelopeAndHidesCost() throws Exception {
        var result = mvc.perform(get("/api/print/materials").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].materialId").value(4))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();
        verify(service).getActiveMaterials(null, 0, 20);
        verify(sessions).validateSession(eq(7), any(UUID.class), any(java.time.Instant.class));
        assertFalse(result.getResponse().getContentAsString().toLowerCase().contains("cost"));
    }

    @Test
    void exactTechnologyAndPaginationAreDelegated() throws Exception {
        mvc.perform(get("/api/print/materials").param("technologyCode", "FDM")
                        .param("page", "1").param("size", "10")
                        .param("sort", "estimatedUnitCost,desc")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        verify(service).getActiveMaterials("FDM", 1, 10);
    }

    @Test
    void emptyCatalogIs200() throws Exception {
        when(service.getActiveMaterials(null, 0, 20))
                .thenReturn(new PagedPrintMaterialResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/print/materials").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void malformedPaginationAndTechnologyReturn400WithoutServiceCall() throws Exception {
        String[][] invalid = {
                {"page", "-1"}, {"page", "abc"}, {"size", "0"}, {"size", "51"},
                {"size", "abc"}, {"technologyCode", ""}, {"technologyCode", " "},
                {"technologyCode", " FDM"}, {"technologyCode", "FDM "},
                {"technologyCode", "x".repeat(31)}
        };
        for (String[] parameter : invalid) {
            mvc.perform(get("/api/print/materials").param(parameter[0], parameter[1])
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void activeDetailIs200WithoutCost() throws Exception {
        var result = mvc.perform(get("/api/print/materials/4").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materialId").value(4))
                .andExpect(jsonPath("$.data.materialName").value("PLA"))
                .andReturn();
        verify(service).getActiveMaterialById(4);
        assertFalse(result.getResponse().getContentAsString().toLowerCase().contains("cost"));
    }

    @Test
    void missingAndInactiveHaveTheSame404() throws Exception {
        when(service.getActiveMaterialById(4))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Material was not found."));
        when(service.getActiveMaterialById(5))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Material was not found."));
        for (int id : List.of(4, 5)) {
            mvc.perform(get("/api/print/materials/" + id).header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Material was not found."));
        }
    }

    @Test
    void invalidDetailIdReturns400() throws Exception {
        for (String id : List.of("0", "-1", "abc", "999999999999999999999")) {
            mvc.perform(get("/api/print/materials/" + id).header("Authorization", token))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingAndInvalidJwtReturn401() throws Exception {
        mvc.perform(get("/api/print/materials")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/print/materials").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectedSessionCannotReachMaterialService() throws Exception {
        doThrow(ApiException.invalidSession()).when(sessions)
                .validateSession(eq(7), any(UUID.class), any(java.time.Instant.class));
        mvc.perform(get("/api/print/materials").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, PrintMaterialController.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean PrintMaterialService materialService() { return mock(PrintMaterialService.class); }
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
