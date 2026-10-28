package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.ai.controller.AiGenerationController;
import com.project.w_3d_world.features.ai.dto.response.AiTaskDetailResponse;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.studio.controller.StudioModelController;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;
import com.project.w_3d_world.features.studio.service.StudioModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AiStudioSecurityWebTest.Config.class)
@WebAppConfiguration
class AiStudioSecurityWebTest {

    @Autowired WebApplicationContext context;
    @Autowired AiGenerationService aiService;
    @Autowired StudioModelService studioService;
    @Autowired JwtService jwt;
    @Autowired AuthSessionService sessionService;

    MockMvc mvc;
    String validToken;
    final int authenticatedUserId = 42;

    @BeforeEach
    void setup() {
        reset(aiService, studioService, sessionService);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        validToken = "Bearer " + jwt.generateToken(authenticatedUserId, Set.of("CUSTOMER"), "", "user42@example.com").getTokenValue();
    }

    @Test
    @DisplayName("Unauthenticated request to /api/ai/text-to-3d returns 401 Unauthorized")
    void unauthenticatedAiRequest_Returns401() throws Exception {
        mvc.perform(post("/api/ai/text-to-3d")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"A medieval shield\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(aiService);
    }

    @Test
    @DisplayName("Unauthenticated request to /api/studio/projects returns 401 Unauthorized")
    void unauthenticatedStudioRequest_Returns401() throws Exception {
        mvc.perform(get("/api/studio/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(studioService);
    }

    @Test
    @DisplayName("Authenticated request to /api/ai/text-to-3d passes user context to service")
    void authenticatedAiRequest_CallsServiceWithPrincipalUserId() throws Exception {
        AiTaskDetailResponse fakeDetail = new AiTaskDetailResponse(
                101, 201, "Shield", "TEXT_TO_3D", "GENERATE", "SUCCESS", 100,
                "MOCK", "job-1", 0, null, Instant.now(), Instant.now(), List.of(), null
        );
        when(aiService.generateText(eq(authenticatedUserId), any())).thenReturn(fakeDetail);

        mvc.perform(post("/api/ai/text-to-3d")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"A medieval shield\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.aiRequestId").value(101));

        verify(aiService).generateText(eq(authenticatedUserId), any());
    }

    @Test
    @DisplayName("Authenticated request to /api/studio/projects lists projects for authenticated user")
    void authenticatedStudioRequest_CallsServiceWithPrincipalUserId() throws Exception {
        when(studioService.listProjects(authenticatedUserId)).thenReturn(List.of());

        mvc.perform(get("/api/studio/projects")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(studioService).listProjects(authenticatedUserId);
    }

    @Test
    @DisplayName("0.2: Storage exception in Studio does NOT return 401 masked error, returns 500")
    void storageException_NotMaskedAs401() throws Exception {
        when(studioService.createProject(eq(authenticatedUserId), any()))
                .thenThrow(new com.project.w_3d_world.features.model.exception.ModelStorageException(
                        com.project.w_3d_world.features.model.exception.ModelStorageException.Code.CONFIGURATION,
                        "Storage root not configured"
                ));

        mvc.perform(post("/api/studio/projects")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Project\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Model storage is unavailable. Please try again later."));
    }

    @Test
    @DisplayName("0.2: Generic unhandled exception does NOT return 401, returns 500 error envelope")
    void unhandledException_NotMaskedAs401() throws Exception {
        when(studioService.createProject(eq(authenticatedUserId), any()))
                .thenThrow(new RuntimeException("Database timeout or unexpected crash"));

        mvc.perform(post("/api/studio/projects")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Project\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AiGenerationController.class, StudioModelController.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean AiGenerationService aiGenerationService() { return mock(AiGenerationService.class); }
        @Bean StudioModelService studioModelService() { return mock(StudioModelService.class); }
        @Bean JsonMapper mapper() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        }
        @Bean JwtService jwt() {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000);
        }
        @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper()));
        }
    }
}
