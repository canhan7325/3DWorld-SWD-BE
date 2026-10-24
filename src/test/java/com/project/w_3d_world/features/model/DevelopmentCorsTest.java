package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.controller.ModelUploadController;
import com.project.w_3d_world.features.model.exception.ModelUploadExceptionHandler;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("dev")
@ContextConfiguration(classes = DevelopmentCorsTest.Config.class)
@WebAppConfiguration
class DevelopmentCorsTest {
    @Autowired WebApplicationContext context;
    @Autowired ModelUploadService uploads;
    @Autowired JwtService jwt;

    @Test
    void arbitraryOriginPreflightSucceedsWithoutJwt() throws Exception {
        MockMvc mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(options("/api/models/uploads")
                        .header("Origin", "https://frontend.example.invalid")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.invalid"))
                .andExpect(header().string("Access-Control-Allow-Credentials", org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(uploads);
    }

    @Test
    void actualUploadStillRequiresJwtAndValidJwtReachesController() throws Exception {
        reset(uploads);
        MockMvc mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(multipart("/api/models/uploads").file(file())
                        .header("Origin", "https://frontend.example.invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.invalid"));
        verifyNoInteractions(uploads);

        when(uploads.uploadModel(anyInt(), any(), any())).thenReturn(
                new ModelUploadResponse(1, 2, 3, "shape", 1, "stl", 12L, "PRIVATE", null));
        String token = jwt.generateToken(7, Set.of("CUSTOMER"), "", "test@example.invalid").getTokenValue();
        mvc.perform(multipart("/api/models/uploads").file(file())
                        .header("Origin", "https://frontend.example.invalid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.invalid"));
        verify(uploads).uploadModel(eq(7), any(), any());
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "shape.stl", "model/stl", new byte[] {1, 2, 3});
    }

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ModelUploadController.class, ModelUploadExceptionHandler.class,
            GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean ModelUploadService uploadService() { return mock(ModelUploadService.class); }
        @Bean AuthSessionService authSessionService() { return mock(AuthSessionService.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
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
