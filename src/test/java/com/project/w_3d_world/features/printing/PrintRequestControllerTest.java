package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.printing.controller.PrintRequestController;
import com.project.w_3d_world.features.printing.service.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.PrintRequestFileDownloadService;
import com.project.w_3d_world.features.printing.service.PrintRequestFileVersionService;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestFileVersionResponse;
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
import java.io.ByteArrayInputStream;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PrintRequestControllerTest.Config.class)
@WebAppConfiguration
class PrintRequestControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired PrintRequestService service;
    @Autowired PrintRequestFileCreateService fileCreate;
    @Autowired PrintRequestFileDownloadService fileDownload;
    @Autowired PrintRequestFileVersionService fileVersions;
    @Autowired AuthSessionService sessions;
    @Autowired JwtService jwt;

    private MockMvc mvc;
    private String customerToken;

    @BeforeEach
    void setUp() {
        reset(service, fileCreate, fileDownload, fileVersions, sessions);
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
    void createDraftUsesJwtOwnerAndReturnsCustomerSafe201() throws Exception {
        var created = new PrintRequestResponse(10, "DRAFT", "Prototype",
                Instant.parse("2026-01-01T00:00:00Z"), null, null, List.of());
        when(fileCreate.create(eq(7), eq(3), eq(true), any())).thenReturn(created);
        var result = mvc.perform(multipart("/api/print/requests/create")
                        .header("Authorization", customerToken)
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true")
                        .file(modelFile()))
                .andExpect(status().isCreated())
                .andExpect(handler().handlerType(PrintRequestController.class))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.printRequestId").value(10))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        verify(fileCreate).create(eq(7), eq(3), eq(true), any());
        verify(sessions).validateSession(eq(7), any(UUID.class), any(Instant.class));
        assertFalse(result.getResponse().getContentAsString().contains("uploadedFileKey"));
        assertFalse(result.getResponse().getContentAsString().contains("estimatedUnitCost"));
    }

    @Test
    void correctionUsesJwtCustomerMultipartAndReturnsCreatedVersion() throws Exception {
        when(fileVersions.correct(eq(7), eq(10), eq(1), any())).thenReturn(
                new PrintRequestFileVersionResponse(10, 2, "corrected.stl", 3L,
                        "model/stl", Instant.parse("2026-10-24T00:00:00Z"), true,
                        "/api/print/requests/getId/10/file/versions/2/download"));
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.current").value(true))
                .andExpect(jsonPath("$.data.downloadPath")
                        .value("/api/print/requests/getId/10/file/versions/2/download"));
        verify(fileVersions).correct(eq(7), eq(10), eq(1), any());
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).header("Authorization", customerToken))
                .andExpect(status().isBadRequest());
        reset(fileVersions);
        when(fileVersions.correct(eq(7), eq(10), eq(1), any())).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", customerToken))
                .andExpect(status().isNotFound());
        reset(fileVersions);
        when(fileVersions.correct(eq(7), eq(10), eq(1), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "Print Request file cannot be changed."));
        mvc.perform(multipart("/api/print/requests/getId/10/file/correct")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", customerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerCanListAndDownloadHistoricalVersion() throws Exception {
        when(fileVersions.list(7, 10)).thenReturn(List.of(
                new PrintRequestFileVersionResponse(10, 1, "original.stl", 4L,
                        "model/stl", Instant.parse("2026-10-24T00:00:00Z"), false,
                        "/api/print/requests/getId/10/file/versions/1/download")));
        mvc.perform(get("/api/print/requests/getId/10/file/versions")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].current").value(false));
        byte[] bytes = {1, 2, 3, 4};
        when(fileVersions.download(7, 10, 1)).thenReturn(new AuthorizedModelDownload(
                new ByteArrayInputStream(bytes), "original.stl", "model/stl", 4L));
        mvc.perform(get("/api/print/requests/getId/10/file/versions/1/download")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(fileVersions).download(7, 10, 1);
    }

    @Test
    void createRejectsMissingFieldsAndJsonContract() throws Exception {
        mvc.perform(post("/api/print/requests/create").header("Authorization", customerToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/print/requests/create").header("Authorization", customerToken)
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/print/requests/create").header("Authorization", customerToken)
                        .param("materialId", "3").file(modelFile()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(fileCreate);
    }

    @Test
    void createRejectsMissingInvalidAndRevokedSessions() throws Exception {
        mvc.perform(multipart("/api/print/requests/create").file(modelFile())
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/print/requests/create").file(modelFile())
                        .header("Authorization", "Bearer invalid")
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isUnauthorized());
        doThrow(ApiException.invalidSession()).when(sessions)
                .validateSession(eq(7), any(UUID.class), any(Instant.class));
        mvc.perform(multipart("/api/print/requests/create").file(modelFile())
                        .header("Authorization", customerToken)
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(fileCreate);
    }

    @Test
    void createRejectsNonCustomerAndPropagatesMissingModel() throws Exception {
        mvc.perform(multipart("/api/print/requests/create").file(modelFile())
                        .header("Authorization", token("PRINT_STAFF"))
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fileCreate);
        when(fileCreate.create(eq(7), eq(3), eq(true), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Model Version was not found."));
        mvc.perform(multipart("/api/print/requests/create").file(modelFile())
                        .header("Authorization", customerToken)
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Model Version was not found."));
        verify(fileCreate).create(eq(7), eq(3), eq(true), any());
    }

    @Test
    void createPersistenceFailureReturnsSafe500() throws Exception {
        when(fileCreate.create(eq(7), eq(3), eq(true), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("internal SQL detail"));
        var result = mvc.perform(multipart("/api/print/requests/create").file(modelFile())
                        .header("Authorization", customerToken)
                        .param("materialId", "3").param("rightsDeclarationAccepted", "true"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("internal SQL detail"));
    }

    @Test
    void ownerDownloadsUnchangedFileBytes() throws Exception {
        byte[] bytes = {0, 1, 2, 3, -1};
        when(fileDownload.open(7, 10)).thenReturn(new AuthorizedModelDownload(
                new ByteArrayInputStream(bytes), "model-5-file-21.glb", "model/gltf-binary", 5L));
        var response = mvc.perform(get("/api/print/requests/getId/10/file")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, response.getContentAsByteArray());
        verify(fileDownload).open(7, 10);
    }

    @Test
    void fileDownloadRequiresSessionAndDoesNotRevealForeignRequests() throws Exception {
        mvc.perform(get("/api/print/requests/getId/10/file"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/print/requests/getId/10/file")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(fileDownload);
        when(fileDownload.open(7, 10))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Print Request file was not found."));
        mvc.perform(get("/api/print/requests/getId/10/file")
                        .header("Authorization", customerToken))
                .andExpect(status().isNotFound());
        verify(fileDownload).open(7, 10);
    }

    private static org.springframework.mock.web.MockMultipartFile modelFile() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", "model.glb", "model/gltf-binary", new byte[] {1, 2, 3});
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
        @Bean PrintRequestFileCreateService printRequestFileCreateService() { return mock(PrintRequestFileCreateService.class); }
        @Bean PrintRequestFileDownloadService printRequestFileDownloadService() { return mock(PrintRequestFileDownloadService.class); }
        @Bean PrintRequestFileVersionService printRequestFileVersionService() { return mock(PrintRequestFileVersionService.class); }
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
