package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.controller.*;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.storage.ModelStorageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Isolated MVC context using the unchanged real BE1 security chain. No Boot/JPA/database startup. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ModelUploadControllerTest.Config.class)
@WebAppConfiguration
class ModelUploadControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired ModelUploadService service;
    @Autowired JwtService jwt;
    MockMvc mvc;
    String token;

    @BeforeEach
    void setup() {
        reset(service);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        token = "Bearer " + jwt.generateToken(7, Set.of("CUSTOMER"), "", "unit@example.invalid").getTokenValue();
        when(service.uploadModel(any(), any(), any())).thenReturn(
                new ModelUploadResponse(101, 201, 301, "Dragon", 1, "glb", 48L, "PRIVATE", null));
    }

    @Test
    void up19_authenticatedMultipart201UsesJwtOwnerAndHidesStorage() throws Exception {
        var result = mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}"))
                        .file(file()).header("Authorization", token).header("ownerUserId", "999").param("ownerUserId", "999"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Model uploaded successfully"))
                .andExpect(jsonPath("$.data.modelId").value(101)).andExpect(jsonPath("$.data.modelVersionId").value(201))
                .andExpect(jsonPath("$.data.fileId").value(301)).andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist()).andExpect(jsonPath("$.timestamp").exists())
                .andReturn();
        verify(service).uploadModel(eq(7), eq(new CreateModelRequest("model", null)), any());
        assertFalse(result.getResponse().getContentAsString().contains("storageRoot"));
    }

    @Test
    void up05_noJwt401FromRealSecurityChain() throws Exception {
        mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}")).file(file()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void invalidJwt401FromRealFilter() throws Exception {
        mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}")).file(file())
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void up06_blankNameMetadataIgnoredForFileOnlyUpload() throws Exception { ignoredMetadata("{\"modelName\":\"   \"}"); }

    @Test
    void up07_longNameMetadataIgnoredForFileOnlyUpload() throws Exception { ignoredMetadata("{\"modelName\":\"" + "x".repeat(256) + "\"}"); }

    @Test
    void controlCharactersAndLongDescriptionMetadataIgnored() throws Exception {
        ignoredMetadata("{\"modelName\":\"bad\\u0000name\"}");
        ignoredMetadata("{\"modelName\":\"Dragon\",\"description\":\"" + "x".repeat(10001) + "\"}");
    }

    @Test
    void malformedJsonAndClientOwnershipMetadataIgnored() throws Exception {
        ignoredMetadata("{bad}");
        for (String field : List.of("ownerUserId", "modelId", "versionId", "storageKey", "modelType", "sourceType")) {
            ignoredMetadata("{\"modelName\":\"Dragon\",\"" + field + "\":\"injected\"}");
        }
    }

    @Test
    void up08_missingFile400WithEnvelope() throws Exception {
        mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}"))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void missingMetadataIsAcceptedForFileOnlyUpload() throws Exception {
        mvc.perform(multipart("/api/models/uploads").file(file()).header("Authorization", token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201));
        verify(service).uploadModel(eq(7), eq(new CreateModelRequest("model", null)), any());
    }

    @ParameterizedTest
    @EnumSource(ModelStorageException.Code.class)
    void storageErrorMappingDoesNotLeakInternalMessages(ModelStorageException.Code code) throws Exception {
        when(service.uploadModel(any(), any(), any())).thenThrow(new ModelStorageException(code, "C:\\private\\sensitive.glb"));
        int statusCode = switch (code) {
            case INVALID_METADATA, INVALID_KEY, EMPTY_FILE, INVALID_CONTENT -> 400;
            case TOO_LARGE -> 413;
            default -> 500;
        };
        var result = mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}"))
                        .file(file()).header("Authorization", token))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.status").value(statusCode)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("sensitive"));
    }

    @Test
    void databaseIntegrity500OverridesAuthSpecificGlobal409OnlyForModelController() throws Exception {
        when(service.uploadModel(any(), any(), any())).thenThrow(new DataIntegrityViolationException("secret SQL"));
        expectServerFailure();
    }

    @Test
    void commitFailure500InsteadOf201() throws Exception {
        when(service.uploadModel(any(), any(), any())).thenThrow(new TransactionSystemException("secret database"));
        expectServerFailure();
    }

    @Test
    void resolvedControllerMultipartLimitException413() throws Exception {
        when(service.uploadModel(any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(50_000_000));
        mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}"))
                        .file(file()).header("Authorization", token))
                .andExpect(status().is(413)).andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void multipartDefaultsAndConfiguredLimitsVerifiedWithoutServerStartup() throws Exception {
        var defaults = new MultipartProperties().createMultipartConfig();
        assertEquals(1_048_576, defaults.getMaxFileSize());
        assertEquals(10_485_760, defaults.getMaxRequestSize());
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-systemEnvironment", Map.of(
                "SPRING_SERVLET_MULTIPART_MAXFILESIZE", "50000000B",
                "SPRING_SERVLET_MULTIPART_MAXREQUESTSIZE", "51000000B",
                "SPRING_SERVLET_MULTIPART_RESOLVELAZILY", "true")));
        var properties = Binder.get(environment).bind("spring.servlet.multipart", MultipartProperties.class).get();
        assertEquals(50_000_000, properties.createMultipartConfig().getMaxFileSize());
        assertEquals(51_000_000, properties.createMultipartConfig().getMaxRequestSize());
        assertTrue(properties.isResolveLazily());

        var configured = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(input);
            configured.load(input);
        }
        var deployedSettings = new MockEnvironment()
                .withProperty("spring.servlet.multipart.max-file-size",
                        configured.getProperty("spring.servlet.multipart.max-file-size"))
                .withProperty("spring.servlet.multipart.max-request-size",
                        configured.getProperty("spring.servlet.multipart.max-request-size"));
        var effective = Binder.get(deployedSettings).bind("spring.servlet.multipart", MultipartProperties.class).get();
        assertEquals(50_000_000, effective.createMultipartConfig().getMaxFileSize());
        assertEquals(51_000_000, effective.createMultipartConfig().getMaxRequestSize());
        assertTrue(effective.createMultipartConfig().getMaxRequestSize() > 11_000_084);
    }

    private void ignoredMetadata(String json) throws Exception {
        mvc.perform(multipart("/api/models/uploads").file(metadata(json)).file(file()).header("Authorization", token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201));
        verify(service, atLeastOnce()).uploadModel(eq(7), eq(new CreateModelRequest("model", null)), any());
    }

    private void expectServerFailure() throws Exception {
        var result = mvc.perform(multipart("/api/models/uploads").file(metadata("{\"modelName\":\"Dragon\"}"))
                        .file(file()).header("Authorization", token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("secret"));
    }

    private static MockMultipartFile metadata(String json) {
        return new MockMultipartFile("metadata", "metadata.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
    private static MockMultipartFile file() throws Exception {
        return new MockMultipartFile("file", "model.glb", "model/gltf-binary", ModelUploadServiceTest.fixture("glb"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ModelUploadController.class, ModelUploadExceptionHandler.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean ModelUploadService service() { return mock(ModelUploadService.class); }
        @Bean JsonMapper mapper() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        }
        @Bean JwtService jwt() {
            byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
            return new JwtService(Base64.getEncoder().encodeToString(secret), 60_000);
        }
        @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper()));
        }
    }
}
