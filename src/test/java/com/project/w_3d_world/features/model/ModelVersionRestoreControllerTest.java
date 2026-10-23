package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.controller.ModelUploadExceptionHandler;
import com.project.w_3d_world.features.model.controller.ModelVersionRestoreController;
import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.RestoreModelVersionResponse;
import com.project.w_3d_world.features.model.dto.response.RestoredModelFileResponse;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.service.ModelVersionRestoreService;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ModelVersionRestoreControllerTest.Config.class)
@WebAppConfiguration
class ModelVersionRestoreControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired ModelVersionRestoreService service;
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
    }

    @Test
    void ownerRestoreReturns201AndSafeFileReferences() throws Exception {
        when(service.restoreVersion(eq(7), eq(15), eq(41), any())).thenReturn(
                new RestoreModelVersionResponse(15, 49, 3, 41, ModelSourceType.EDITOR_SAVE,
                        "Restore previous model version", Instant.parse("2026-10-23T16:00:00Z"),
                        List.of(new RestoredModelFileResponse(90, ModelFileRole.SOURCE_MODEL,
                                "stl", 4L, true, "/api/models/15/files/90/download"))));
        var result = mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json")
                        .content("{\"baseVersionId\":42,\"changeNote\":\"Restore previous model version\"}")
                        .header("Authorization", token).param("ownerUserId", "999"))
                .andExpect(status().isCreated())
                .andExpect(handler().handlerType(ModelVersionRestoreController.class))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.modelId").value(15))
                .andExpect(jsonPath("$.data.modelVersionId").value(49))
                .andExpect(jsonPath("$.data.versionNumber").value(3))
                .andExpect(jsonPath("$.data.parentVersionId").value(41))
                .andExpect(jsonPath("$.data.sourceType").value("EDITOR_SAVE"))
                .andExpect(jsonPath("$.data.files[0].fileRole").value("SOURCE_MODEL"))
                .andExpect(jsonPath("$.data.files[0].downloadPath")
                        .value("/api/models/15/files/90/download"))
                .andReturn();
        verify(service).restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, "Restore previous model version"));
        verify(sessions).validateSession(eq(7), any(UUID.class), any(Instant.class));
        assertFalse(result.getResponse().getContentAsString().contains("storageKey"));
    }

    @Test
    void missingInvalidAndRevokedAuthenticationCannotRestore() throws Exception {
        mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        doThrow(ApiException.invalidSession()).when(sessions)
                .validateSession(eq(7), any(UUID.class), any(Instant.class));
        mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}")
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidBodyAndPathValuesAreRejectedBeforeService() throws Exception {
        for (String body : new String[]{"{\"baseVersionId\":0}", "{\"baseVersionId\":-1}",
                "{\"changeNote\":\"missing base\"}", "{bad}"}) {
            mvc.perform(post("/api/models/15/versions/41/restore")
                            .contentType("application/json").content(body)
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/models/not-a-number/versions/41/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/models/15/versions/0/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void staleAndInaccessibleVersionsPreserveTheirStatuses() throws Exception {
        when(service.restoreVersion(eq(7), eq(15), eq(41), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "The base Model Version is stale."));
        mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}")
                        .header("Authorization", token))
                .andExpect(status().isConflict());
        reset(service);
        when(service.restoreVersion(eq(7), eq(15), eq(41), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Model or version was not found."));
        mvc.perform(post("/api/models/15/versions/41/restore")
                        .contentType("application/json").content("{\"baseVersionId\":42}")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ModelVersionRestoreController.class,
            ModelUploadExceptionHandler.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean ModelVersionRestoreService restoreService() { return mock(ModelVersionRestoreService.class); }
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
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
