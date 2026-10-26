package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.exception.ModelUploadExceptionHandler;
import com.project.w_3d_world.features.model.controller.ModelVersionSaveController;
import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.dto.response.SaveModelVersionResponse;
import com.project.w_3d_world.features.model.dto.response.SavedModelFileResponse;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.service.ModelVersionSaveService;
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
import org.springframework.mock.web.MockMultipartFile;
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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ModelVersionSaveControllerTest.Config.class)
@WebAppConfiguration
class ModelVersionSaveControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired ModelVersionSaveService service;
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
    void ownerMultipartSaveReturns201WithServerVersionAndSafeFileReference() throws Exception {
        when(service.saveVersion(eq(7), eq(15), any(), any())).thenReturn(
                new SaveModelVersionResponse(15, 42, 2, 41, ModelSourceType.EDITOR_SAVE,
                        "Updated model geometry", Instant.parse("2026-10-23T14:30:00Z"),
                        new SavedModelFileResponse(89, "model-15-file-89.stl", "stl", 4L,
                                "/api/models/15/files/89/download")));
        var result = mvc.perform(multipart("/api/models/15/versions/create")
                        .file(metadata("{\"baseVersionId\":41,\"changeNote\":\"Updated model geometry\"}"))
                        .file(file()).header("Authorization", token)
                        .param("ownerUserId", "999"))
                .andExpect(status().isCreated())
                .andExpect(handler().handlerType(ModelVersionSaveController.class))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.modelId").value(15))
                .andExpect(jsonPath("$.data.modelVersionId").value(42))
                .andExpect(jsonPath("$.data.versionNumber").value(2))
                .andExpect(jsonPath("$.data.parentVersionId").value(41))
                .andExpect(jsonPath("$.data.sourceType").value("EDITOR_SAVE"))
                .andExpect(jsonPath("$.data.file.fileId").value(89))
                .andExpect(jsonPath("$.data.file.downloadPath")
                        .value("/api/models/15/files/89/download"))
                .andReturn();
        verify(service).saveVersion(eq(7), eq(15),
                eq(new SaveModelVersionRequest(41, "Updated model geometry")), argThat(fileMatching()));
        verify(sessions).validateSession(eq(7), any(UUID.class), any(Instant.class));
        assertFalse(result.getResponse().getContentAsString().contains("storageKey"));
    }

    @Test
    void missingInvalidAndRevokedSessionsCannotSave() throws Exception {
        mvc.perform(multipart("/api/models/15/versions/create").file(metadata("{\"baseVersionId\":41}"))
                        .file(file()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/models/15/versions/create").file(metadata("{\"baseVersionId\":41}"))
                        .file(file()).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        doThrow(ApiException.invalidSession()).when(sessions)
                .validateSession(eq(7), any(UUID.class), any(Instant.class));
        mvc.perform(multipart("/api/models/15/versions/create").file(metadata("{\"baseVersionId\":41}"))
                        .file(file()).header("Authorization", token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidMetadataAndMissingPartsAreRejectedBeforeService() throws Exception {
        for (String json : new String[]{"{\"baseVersionId\":0}", "{\"baseVersionId\":-1}",
                "{\"changeNote\":\"missing base\"}", "{bad}"}) {
            mvc.perform(multipart("/api/models/15/versions/create").file(metadata(json))
                            .file(file()).header("Authorization", token))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/models/15/versions/create").file(file())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/models/15/versions/create").file(metadata("{\"baseVersionId\":41}"))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/models/not-a-number/versions/create")
                        .file(metadata("{\"baseVersionId\":41}")).file(file())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void staleAndInaccessibleModelsKeepTheirServiceStatus() throws Exception {
        when(service.saveVersion(eq(7), eq(15), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "The base Model Version is stale."));
        mvc.perform(multipart("/api/models/15/versions/create")
                        .file(metadata("{\"baseVersionId\":41}")).file(file())
                        .header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        reset(service);
        when(service.saveVersion(eq(7), eq(15), any(), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Model or version was not found."));
        mvc.perform(multipart("/api/models/15/versions/create")
                        .file(metadata("{\"baseVersionId\":41}")).file(file())
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Model or version was not found."));
    }

    private static MockMultipartFile metadata(String json) {
        return new MockMultipartFile("metadata", "metadata.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "Dragon.stl", "model/stl", new byte[]{1, 2, 3, 4});
    }

    private static org.mockito.ArgumentMatcher<org.springframework.web.multipart.MultipartFile> fileMatching() {
        return value -> "Dragon.stl".equals(value.getOriginalFilename()) && value.getSize() == 4;
    }

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ModelVersionSaveController.class,
            ModelUploadExceptionHandler.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean ModelVersionSaveService saveService() { return mock(ModelVersionSaveService.class); }
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
