package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.controller.*;
import com.project.w_3d_world.features.model.dto.response.*;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
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
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ModelLibraryControllerTest.Config.class)
@WebAppConfiguration
class ModelLibraryControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired ModelLibraryService service;
    @Autowired JwtService jwt;
    @Autowired AuthSessionService sessionService;
    @Autowired ModelUploadService uploadService;
    MockMvc mvc;
    String token;

    @BeforeEach
    void setup() {
        reset(service, uploadService, sessionService);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        token = "Bearer " + jwt.generateToken(7, Set.of("CUSTOMER"), "", "unit@example.invalid").getTokenValue();
        when(service.getMyModels(anyInt(), anyInt(), anyInt())).thenReturn(
                new PagedModelResponse(List.of(new ModelSummaryResponse(11, "Dragon", "desc",
                        ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE", Instant.parse("2026-01-01T00:00:00Z"))), 0, 20, 1, 1));
        when(service.getModelDetails(anyInt(), anyInt())).thenReturn(
                new ModelDetailResponse(11, "Dragon", "desc", ModelType.WEB_EDITOR, ModelOriginType.UPLOAD,
                        "PRIVATE", new LatestModelVersionResponse(21, 1, ModelSourceType.UPLOAD),
                        Instant.parse("2026-01-01T00:00:00Z"), null));
    }

    @Test
    void mvc01_myLibraryUsesJwtOwnerAndApiResponse() throws Exception {
        mvc.perform(get("/api/models/my").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Models retrieved successfully"))
                .andExpect(jsonPath("$.data.content[0].modelId").value(11))
                .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[0].ownerUserId").doesNotExist());
        verify(service).getMyModels(7, 0, 20);
    }

    @Test
    void mvc02_missingJwt401() throws Exception {
        mvc.perform(get("/api/models/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void mvc03_invalidJwt401() throws Exception {
        mvc.perform(get("/api/models/my").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void validJwtWithRevokedSessionIsRejected() throws Exception {
        doThrow(com.project.w_3d_world.common.exception.ApiException.invalidSession())
                .when(sessionService).validateSession(eq(7), any(java.util.UUID.class), any(java.time.Instant.class));
        mvc.perform(get("/api/models/my").header("Authorization", token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verify(sessionService).validateSession(eq(7), any(java.util.UUID.class), any(java.time.Instant.class));
        verifyNoInteractions(service);
    }

    @Test
    void mvc04_detailsAndMyRouteDoNotConflict() throws Exception {
        mvc.perform(get("/api/models/11").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Model retrieved successfully"))
                .andExpect(jsonPath("$.data.latestVersion.modelVersionId").value(21))
                .andExpect(jsonPath("$.data.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
        verify(service).getModelDetails(7, 11);
        mvc.perform(get("/api/models/my").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void mvc06_mvc07_invalidPaging400() throws Exception {
        for (String[] query : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"},
                new String[]{"size", "51"}, new String[]{"page", "nope"}, new String[]{"size", "nope"})) {
            mvc.perform(get("/api/models/my").param(query[0], query[1]).header("Authorization", token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void mvc05_modelNotFoundReturns404WithoutDisclosure() throws Exception {
        when(service.getModelDetails(7, 99)).thenThrow(new com.project.w_3d_world.common.exception.ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Model was not found."));
        var result = mvc.perform(get("/api/models/99").header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("ownerUserId"));
    }

    @Test
    void mvc08_emptyLibrary200() throws Exception {
        when(service.getMyModels(7, 0, 20)).thenReturn(new PagedModelResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/models/my").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void mvc04_invalidModelId400() throws Exception {
        mvc.perform(get("/api/models/not-a-number").header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void mvc10_existingUploadRouteStillReturns201() throws Exception {
        when(uploadService.uploadModel(anyInt(), any(), any())).thenReturn(
                new ModelUploadResponse(1, 2, 3, "Dragon", 1, "glb", 48L, "PRIVATE", Instant.now()));
        mvc.perform(multipart("/api/models/uploads")
                        .file(new org.springframework.mock.web.MockMultipartFile("metadata", "metadata.json", "application/json",
                                "{\"modelName\":\"Dragon\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "model.glb", "model/gltf-binary", new byte[]{1}))
                        .header("Authorization", token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201))
                .andExpect(handler().handlerType(ModelUploadController.class));
        verify(uploadService).uploadModel(eq(7), any(), any());
        verifyNoInteractions(service);
    }

    @Test
    void clientOwnerAndSortCannotOverrideIdentityOrOrdering() throws Exception {
        mvc.perform(get("/api/models/my").param("ownerUserId", "999").param("sort", "modelName,asc")
                        .param("page", "2").param("size", "50").header("ownerUserId", "999")
                        .header("Authorization", token))
                .andExpect(status().isOk()).andExpect(handler().methodName("getMyModels"));
        verify(service).getMyModels(7, 2, 50);
    }

    @Test
    void databaseFailuresHaveControlled500WithoutInternalDetails() throws Exception {
        when(service.getMyModels(anyInt(), anyInt(), anyInt())).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("secret connection details"));
        var response = mvc.perform(get("/api/models/my").header("Authorization", token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500)).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("secret"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ModelLibraryController.class, ModelUploadController.class,
            ModelUploadExceptionHandler.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean ModelLibraryService libraryService() { return mock(ModelLibraryService.class); }
        @Bean ModelUploadService uploadService() { return mock(ModelUploadService.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(); }
        @Bean JwtService jwt() { byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret); return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000); }
        @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper()));
        }
    }
}
