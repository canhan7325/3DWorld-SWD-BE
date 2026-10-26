package com.project.w_3d_world.features.model;

import com.project.w_3d_world.features.model.exception.ModelUploadExceptionHandler;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.controller.*;
import com.project.w_3d_world.features.model.dto.response.*;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.security.SecureRandom;
import java.time.Instant;
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
@ContextConfiguration(classes = ModelVersionHistoryControllerTest.Config.class)
@WebAppConfiguration
class ModelVersionHistoryControllerTest {
    @Autowired WebApplicationContext context; @Autowired ModelVersionHistoryService service;
    @Autowired ModelLibraryService library; @Autowired ModelUploadService upload; @Autowired JwtService jwt;
    MockMvc mvc; String token;
    @BeforeEach void setup() {
        reset(service, library, upload); mvc = webAppContextSetup(context).apply(springSecurity()).build();
        token = "Bearer " + jwt.generateToken(7, Set.of("CUSTOMER"), "", "unit@example.invalid").getTokenValue();
        when(service.getModelVersions(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(new PagedModelVersionsResponse(
                List.of(new ModelVersionSummaryResponse(101, 1, ModelSourceType.UPLOAD, null, null, Instant.now())), 0, 20, 1, 1));
        when(service.getModelVersionDetails(anyInt(), anyInt(), anyInt())).thenReturn(new ModelVersionDetailResponse(
                101, 10, 1, ModelSourceType.UPLOAD, null, null, Instant.now(), List.of(new ModelFileInfoResponse(301, ModelFileRole.SOURCE_MODEL, "glb", 20L, true))));
    }
    @Test void mvc01_history200AndResponseSafe() throws Exception {
        mvc.perform(get("/api/models/getId/10/versions/getAll").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].modelVersionId").value(101))
                .andExpect(jsonPath("$.data.content[0].storageKey").doesNotExist());
        verify(service).getModelVersions(7, 10, 0, 20);
    }
    @Test void mvc02_details200AndNoSensitiveFields() throws Exception {
        var result = mvc.perform(get("/api/models/getId/10/versions/getId/101").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.modelId").value(10))
                .andExpect(jsonPath("$.data.files[0].fileId").value(301))
                .andExpect(jsonPath("$.data.files[0].storageKey").doesNotExist()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("hash")); verify(service).getModelVersionDetails(7, 10, 101);
    }
    @Test void mvc03_mvc04_missingAndInvalidJwt401() throws Exception {
        mvc.perform(get("/api/models/getId/10/versions/getAll")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/models/getId/10/versions/getAll").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void mvc08_mvc09_invalidParams400() throws Exception {
        mvc.perform(get("/api/models/getId/-1/versions/getAll").header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/models/getId/10/versions/getAll").param("page", "-1")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/models/getId/10/versions/getAll").param("size", "51")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/models/getId/10/versions/getId/0").header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/models/getId/nope/versions/getAll").header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
    @Test void mvc10_mvc11_emptyHistoryAndNoFiles200() throws Exception {
        when(service.getModelVersions(7, 10, 0, 20)).thenReturn(new PagedModelVersionsResponse(List.of(), 0, 20, 0, 0));
        when(service.getModelVersionDetails(7, 10, 101)).thenReturn(new ModelVersionDetailResponse(101, 10, 1, ModelSourceType.UPLOAD, null, null, Instant.now(), List.of()));
        mvc.perform(get("/api/models/getId/10/versions/getAll").header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/models/getId/10/versions/getId/101").header("Authorization", token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.files").isEmpty());
        verify(service).getModelVersions(7, 10, 0, 20);
        verify(service).getModelVersionDetails(7, 10, 101);
    }
    @Test void mvc05_mvc06_notFoundDoesNotDiscloseOwnership() throws Exception {
        when(service.getModelVersions(7, 99, 0, 20)).thenThrow(new com.project.w_3d_world.common.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Model or version was not found."));
        var result = mvc.perform(get("/api/models/getId/99/versions/getAll").header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Model or version was not found."))
                .andExpect(handler().handlerType(ModelVersionHistoryController.class)).andReturn();
        verify(service).getModelVersions(7, 99, 0, 20);
        assertFalse(result.getResponse().getContentAsString().contains("ownerUserId"));
    }
    @Test void mvc14_mvc15_existingRoutesStillResolve() throws Exception {
        when(library.getMyModels(7, 0, 20)).thenReturn(new com.project.w_3d_world.features.model.dto.response.PagedModelResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/models/getAll").header("Authorization", token)).andExpect(status().isOk()).andExpect(handler().handlerType(ModelLibraryController.class));
        when(library.getModelDetails(7, 10)).thenReturn(new ModelDetailResponse(
                10, "Dragon", "desc", ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE",
                new LatestModelVersionResponse(101, 1, ModelSourceType.UPLOAD), Instant.now(), null));
        mvc.perform(get("/api/models/getId/10").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(handler().handlerType(ModelLibraryController.class))
                .andExpect(jsonPath("$.data.modelId").value(10));
        verify(library).getMyModels(7, 0, 20);
        verify(library).getModelDetails(7, 10);
        verifyNoInteractions(service);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ModelVersionHistoryController.class, ModelLibraryController.class, ModelUploadController.class, ModelUploadExceptionHandler.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean ModelVersionHistoryService service() { return mock(ModelVersionHistoryService.class); }
        @Bean ModelLibraryService library() { return mock(ModelLibraryService.class); }
        @Bean ModelUploadService upload() { return mock(ModelUploadService.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(); }
        @Bean JwtService jwt() { byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret); return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000); }
        @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) { builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper())); }
    }
}
