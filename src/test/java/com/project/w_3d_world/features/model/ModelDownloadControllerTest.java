package com.project.w_3d_world.features.model;

import com.project.w_3d_world.features.model.exception.ModelUploadExceptionHandler;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.features.model.controller.*;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelLibraryService;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelResponse;
import com.project.w_3d_world.features.model.dto.response.ModelDetailResponse;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import org.junit.jupiter.api.*;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ModelDownloadControllerTest.Config.class)
@WebAppConfiguration
class ModelDownloadControllerTest {
    @Autowired WebApplicationContext context; @Autowired ModelDownloadService service; @Autowired ModelLibraryService library; @Autowired ModelUploadService upload; @Autowired JwtService jwt;
    MockMvc mvc; String token;
    @BeforeEach void setup() { reset(service, library, upload); mvc = webAppContextSetup(context).apply(springSecurity()).build(); token = "Bearer " + jwt.generateToken(7, Set.of("CUSTOMER"), "", "unit@example.invalid").getTokenValue(); when(service.openAuthorizedDownload(anyInt(), anyInt(), anyInt())).thenReturn(new AuthorizedModelDownload(new ByteArrayInputStream(new byte[]{1,2,3}), "model-10-file-301.glb", "model/gltf-binary", null)); }
    @Test void mvcDl01_binaryAndHeaders() throws Exception { mvc.perform(get("/api/models/10/files/301/download").header("Authorization", token)).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3})).andExpect(header().string("Content-Disposition", "attachment; filename=\"model-10-file-301.glb\"")).andExpect(header().string("Content-Type", "model/gltf-binary")).andExpect(header().string("Cache-Control", "private, no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff")); verify(service).openAuthorizedDownload(7,10,301); }
    @Test void mvcDl02_dl03_missingInvalidJwt401() throws Exception { mvc.perform(get("/api/models/10/files/301/download")).andExpect(status().isUnauthorized()); mvc.perform(get("/api/models/10/files/301/download").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized()); verifyNoInteractions(service); }
    @Test void mvcDl06_invalidIds400() throws Exception { for (String path : List.of("/api/models/0/files/301/download", "/api/models/10/files/0/download", "/api/models/x/files/301/download")) mvc.perform(get(path).header("Authorization", token)).andExpect(status().isBadRequest()); verifyNoInteractions(service); }
    @Test void mvcDl04_dl05_notFound404() throws Exception { when(service.openAuthorizedDownload(7,10,301)).thenThrow(new com.project.w_3d_world.common.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Model file was not found.")); var result = mvc.perform(get("/api/models/10/files/301/download").header("Authorization", token)).andExpect(status().isNotFound()).andReturn(); assertFalse(result.getResponse().getContentAsString().contains("storage")); }
    @Test void mvcDl12_storageFailure500Controlled() throws Exception { when(service.openAuthorizedDownload(7,10,301)).thenThrow(new com.project.w_3d_world.common.exception.ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "The model file is temporarily unavailable.")); mvc.perform(get("/api/models/10/files/301/download").header("Authorization", token)).andExpect(status().isInternalServerError()); }
    @Test void mvcDl16_existingRoutesNoConflict() throws Exception { when(library.getMyModels(7,0,20)).thenReturn(new PagedModelResponse(List.of(),0,20,0,0)); mvc.perform(get("/api/models/my").header("Authorization", token)).andExpect(status().isOk()).andExpect(handler().handlerType(ModelLibraryController.class)); verifyNoInteractions(service); }
    @Configuration @EnableWebMvc @EnableWebSecurity @Import({SecurityConfig.class, ModelDownloadController.class, ModelLibraryController.class, ModelUploadController.class, ModelUploadExceptionHandler.class, GlobalExceptionHandler.class}) static class Config implements WebMvcConfigurer {
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean ModelDownloadService service() { return mock(ModelDownloadService.class); } @Bean ModelLibraryService library() { return mock(ModelLibraryService.class); } @Bean ModelUploadService upload() { return mock(ModelUploadService.class); } @Bean JsonMapper mapper() { return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(); } @Bean JwtService jwt() { byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret); return new JwtService(Base64.getEncoder().encodeToString(secret),60000); } @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) { builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper())); }
    }
}
