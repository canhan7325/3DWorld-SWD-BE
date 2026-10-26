package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.printing.controller.PrintRequestController;
import com.project.w_3d_world.features.printing.controller.StaffPrintRequestController;
import com.project.w_3d_world.features.printing.controller.StaffPrintProductionController;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteResponse;
import com.project.w_3d_world.features.printing.exception.PrintRequestExceptionHandler;
import com.project.w_3d_world.features.printing.exception.StaffPrintRequestExceptionHandler;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestQuoteService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestWorkflowService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingAddressService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingReadinessService;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestShippingAddressResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestShippingReadinessResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestWorkflowResponse;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffAccessService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffQueueService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestProductionManagementService;
import com.project.w_3d_world.features.printing.dto.response.PagedStaffProductionBoardResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffProductionBoardItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintProductionSummaryResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintProductionItemTransitionResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffProductionDetailResponse;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestTechnicalReviewService;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestTechnicalReviewResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedStaffReviewQueueResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffReviewQueueItemResponse;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileDownloadService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileVersionService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestSpecificationService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStateService;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSpecVersionResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestFileVersionResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSummaryResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStateResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStatusHistoryResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @Autowired PrintRequestSpecificationService specifications;
    @Autowired PrintRequestStateService states;
    @Autowired PrintRequestStaffAccessService staffAccess;
    @Autowired PrintRequestStaffQueueService staffQueue;
    @Autowired PrintRequestProductionManagementService production;
    @Autowired PrintRequestTechnicalReviewService staffReviews;
    @Autowired PrintRequestQuoteService quotes;
    @Autowired PrintRequestWorkflowService workflow;
    @Autowired PrintRequestShippingAddressService shippingAddress;
    @Autowired PrintRequestShippingReadinessService shippingReadiness;
    @Autowired AuthSessionService sessions;
    @Autowired JwtService jwt;

    private MockMvc mvc;
    private String customerToken;

    @BeforeEach
    void setUp() {
        reset(service, fileCreate, fileDownload, fileVersions, specifications, states,
                staffAccess, staffQueue, staffReviews, quotes, production, workflow,
                shippingAddress, shippingReadiness, sessions);
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
        when(workflow.forCustomer(7, 10)).thenReturn(new PrintRequestWorkflowResponse(
                "DRAFT", null, null, null, 1, 1,
                new PrintRequestWorkflowResponse.AllowedActions(
                        true, true, false, false, false, false, false, true)));
        when(shippingReadiness.forCustomer(7, 10)).thenReturn(
                new PrintRequestShippingReadinessResponse(false,
                        List.of("NOT_IN_PRODUCTION_QUEUE"), 0, 1, false));
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
    void customerSelectsOwnedShippingAddressAndReadsReadiness() throws Exception {
        when(shippingAddress.select(7, 10, 3))
                .thenReturn(new PrintRequestShippingAddressResponse(10, 3));
        mvc.perform(patch("/api/print/requests/getId/10/updateShippingAddress")
                        .header("Authorization", customerToken)
                        .contentType("application/json").content("{\"addressId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingAddressId").value(3));
        mvc.perform(get("/api/print/requests/getId/10")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipping.shippingReady").value(false))
                .andExpect(jsonPath("$.data.workflow.displayStatus").value("DRAFT"));
        mvc.perform(patch("/api/print/requests/getId/10/updateShippingAddress")
                        .header("Authorization", customerToken)
                        .contentType("application/json").content("{\"addressId\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/print/requests/getId/10/updateShippingAddress")
                        .header("Authorization", token("PRINT_STAFF"))
                        .contentType("application/json").content("{\"addressId\":3}"))
                .andExpect(status().isForbidden());
        verify(shippingAddress).select(7, 10, 3);
    }

    @Test
    void previewAndStatusHistoryRequireCustomerIdentity() throws Exception {
        when(states.confirmPreview(eq(7), eq(10), any())).thenReturn(
                new PrintRequestStateResponse(10, "DRAFT", true, true, 2, 3,
                        null, null, Instant.parse("2026-10-24T00:00:00Z")));
        when(states.history(7, 10)).thenReturn(List.of(
                new PrintRequestStatusHistoryResponse(1, null, "DRAFT",
                        Instant.parse("2026-10-24T00:00:00Z"), 7, null)));
        mvc.perform(post("/api/print/requests/getId/10/preview/confirm")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":2,\"expectedSpecVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewConfirmed").value(true));
        mvc.perform(get("/api/print/requests/getId/10/status-history/getAll")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].toStatus").value("DRAFT"));
        mvc.perform(post("/api/print/requests/getId/10/preview/confirm")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":0,\"expectedSpecVersion\":3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/print/requests/getId/10/status-history/getAll"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/print/requests/getId/10/status-history/getAll")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isForbidden());
        verify(states).confirmPreview(eq(7), eq(10), any());
        verify(states).history(7, 10);
    }

    @Test
    void submitUsesCustomerIdentityAndExpectedVersions() throws Exception {
        when(states.submit(eq(7), eq(10), any())).thenReturn(
                new PrintRequestStateResponse(10, "SUBMITTED", true, true, 2, 3,
                        Instant.parse("2026-10-24T00:00:00Z"), null,
                        Instant.parse("2026-10-24T00:00:00Z")));
        mvc.perform(post("/api/print/requests/getId/10/submit")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":2,\"expectedSpecVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(post("/api/print/requests/getId/10/submit")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/print/requests/getId/10/submit")
                        .header("Authorization", token("PRINT_STAFF"))
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":2,\"expectedSpecVersion\":3}"))
                .andExpect(status().isForbidden());
        verify(states).submit(eq(7), eq(10), any());
    }

    @Test
    void cancelRequiresCustomerAndReason() throws Exception {
        when(states.cancel(eq(7), eq(10), any())).thenReturn(
                new PrintRequestStateResponse(10, "CANCELLED", true, false, 1, 1,
                        null, Instant.parse("2026-10-24T00:00:00Z"),
                        Instant.parse("2026-10-24T00:00:00Z")));
        mvc.perform(post("/api/print/requests/getId/10/cancel")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"No longer needed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/print/requests/getId/10/cancel")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/print/requests/getId/10/cancel")
                        .header("Authorization", token("PRINT_STAFF"))
                        .contentType("application/json")
                        .content("{\"reason\":\"No longer needed\"}"))
                .andExpect(status().isForbidden());
        verify(states).cancel(eq(7), eq(10), any());
    }

    @Test
    void correctionUsesJwtCustomerMultipartAndReturnsCreatedVersion() throws Exception {
        when(fileVersions.correct(eq(7), eq(10), eq(1), any())).thenReturn(
                new PrintRequestFileVersionResponse(10, 2, "corrected.stl", 3L,
                        "model/stl", Instant.parse("2026-10-24T00:00:00Z"), true,
                        "/api/print/requests/getId/10/file/versions/2/download"));
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.current").value(true))
                .andExpect(jsonPath("$.data.downloadPath")
                        .value("/api/print/requests/getId/10/file/versions/2/download"));
        verify(fileVersions).correct(eq(7), eq(10), eq(1), any());
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
                        .file(modelFile()).header("Authorization", customerToken))
                .andExpect(status().isBadRequest());
        reset(fileVersions);
        when(fileVersions.correct(eq(7), eq(10), eq(1), any())).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
                        .file(modelFile()).param("expectedCurrentFileVersion", "1")
                        .header("Authorization", customerToken))
                .andExpect(status().isNotFound());
        reset(fileVersions);
        when(fileVersions.correct(eq(7), eq(10), eq(1), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "Print Request file cannot be changed."));
        mvc.perform(multipart("/api/print/requests/getId/10/file/versions/create")
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
        mvc.perform(get("/api/print/requests/getId/10/file/versions/getAll")
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
    void customerCanUpdateAndReadSpecificationVersions() throws Exception {
        var version = new PrintRequestSpecVersionResponse(101, 2,
                JsonMapper.builder().build().readTree("{\"items\":[{\"materialId\":3}]}"),
                Instant.parse("2026-10-24T00:00:00Z"), 7, true);
        when(specifications.update(eq(7), eq(10), any())).thenReturn(version);
        var body = "{\"expectedCurrentSpecVersion\":1,\"items\":[{\"printRequestItemId\":21,"
                + "\"materialId\":3,\"quantity\":2}]}";
        mvc.perform(patch("/api/print/requests/getId/10/updateSpecification")
                        .contentType("application/json").content(body)
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.specificationData.items[0].materialId").value(3))
                .andExpect(jsonPath("$.data.isCurrent").value(true));
        verify(specifications).update(eq(7), eq(10), any());

        when(specifications.history(7, 10)).thenReturn(List.of(version));
        mvc.perform(get("/api/print/requests/getId/10/specifications/versions/getAll")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(2));
        verify(specifications).history(7, 10);
    }

    @Test
    void specificationRoutesRequireCustomerAndPreserveOwnerAndStaleStatuses() throws Exception {
        var body = "{\"expectedCurrentSpecVersion\":1,\"items\":[{\"printRequestItemId\":21,"
                + "\"materialId\":3,\"quantity\":2}]}";
        mvc.perform(patch("/api/print/requests/getId/10/updateSpecification")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/print/requests/getId/10/updateSpecification")
                        .contentType("application/json").content(body)
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/print/requests/getId/10/specifications/versions/getAll")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/print/requests/getId/10/updateSpecification")
                        .contentType("application/json")
                        .content("{\"items\":[]}").header("Authorization", customerToken))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(specifications);

        when(specifications.update(eq(7), eq(10), any())).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        mvc.perform(patch("/api/print/requests/getId/10/updateSpecification")
                        .contentType("application/json").content(body)
                        .header("Authorization", customerToken))
                .andExpect(status().isNotFound());
        reset(specifications);
        when(specifications.update(eq(7), eq(10), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "The spec version is stale."));
        mvc.perform(patch("/api/print/requests/getId/10/updateSpecification")
                        .contentType("application/json").content(body)
                        .header("Authorization", customerToken))
                .andExpect(status().isConflict());
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
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"displayStatus\":\"DRAFT\""));
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
    void staffRoutesRequireStaffOrAdminAndReturnNoStorageKey() throws Exception {
        when(staffAccess.detail(10)).thenReturn(new StaffPrintRequestResponse(
                10, "SUBMITTED", Instant.parse("2026-10-25T00:00:00Z"), 1, 1,
                new PrintRequestFileVersionResponse(10, 1, "shape.stl", 3L,
                        "model/stl", Instant.parse("2026-10-25T00:00:00Z"), true,
                        "/api/staff/print/requests/getId/10/file/download"),
                JsonMapper.builder().build().readTree("{\"items\":[]}"), List.of(), List.of()));
        var result = mvc.perform(get("/api/staff/print/requests/getId/10")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentFileVersion").value(1))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("storageKey"));
        mvc.perform(get("/api/staff/print/requests/getId/10")
                        .header("Authorization", token("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/staff/print/requests/getId/10")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/print/requests/getId/10"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/print/requests/getId/10")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verify(staffAccess, times(2)).detail(10);
    }

    @Test
    void staffQueueAndClaimRequireRoleAndExposeAssignmentOnly() throws Exception {
        var item = new StaffReviewQueueItemResponse(10,
                Instant.parse("2026-10-25T00:00:00Z"), "SUBMITTED", null, null,
                2, 1, true, false, true);
        when(staffQueue.list(7, "UNASSIGNED", 1, 10)).thenReturn(
                new PagedStaffReviewQueueResponse(List.of(item), 1, 10, 11, 2));
        when(staffQueue.claim(7, 10)).thenReturn(new StaffReviewQueueItemResponse(10,
                item.submittedAt(), "SUBMITTED", 7, Instant.now(), 2, 1, true, false, true));

        mvc.perform(get("/api/staff/print/requests/getAll")
                        .header("Authorization", token("PRINT_STAFF"))
                        .param("assignment", "UNASSIGNED").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].printRequestId").value(10))
                .andExpect(jsonPath("$.data.content[0].assignedStaffId").isEmpty())
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(post("/api/staff/print/requests/10/claim")
                        .header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedStaffId").value(7));
        mvc.perform(get("/api/staff/print/requests/getAll").header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/staff/print/requests/10/claim")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/print/requests/getAll"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/print/requests/getAll")
                        .header("Authorization", token("PRINT_STAFF"))
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
        verify(staffQueue).list(7, "UNASSIGNED", 1, 10);
        verify(staffQueue).claim(7, 10);
    }

    @Test
    void productionBoardAndActionsRequireStaffRole() throws Exception {
        var summary = new PrintProductionSummaryResponse(2, 2, 0, 0, "QUEUED");
        when(production.board("QUEUED", 0, 10)).thenReturn(new PagedStaffProductionBoardResponse(
                List.of(new StaffProductionBoardItemResponse(10, "IN_QUEUE", Instant.now(),
                        9, 55, 2, 1, summary)), 0, 10, 1, 1));
        when(production.start(7, 10, 21)).thenReturn(new PrintProductionItemTransitionResponse(
                10, 21, "PRINTING", new PrintProductionSummaryResponse(2, 1, 1, 0, "PRINTING")));
        when(production.detail(10)).thenReturn(new StaffProductionDetailResponse(
                10, "IN_QUEUE", Instant.now(), 9, 55, 2, 1, summary, List.of(), List.of()));
        when(shippingReadiness.forStaff(10)).thenReturn(new PrintRequestShippingReadinessResponse(
                false, List.of("SHIPPING_ADDRESS_MISSING"), 2, 2, false));
        when(production.complete(7, 10, 21)).thenReturn(new PrintProductionItemTransitionResponse(
                10, 21, "COMPLETED", new PrintProductionSummaryResponse(2, 1, 0, 1, "PRINTING")));
        mvc.perform(get("/api/staff/print/production/getAll").param("itemStatus", "QUEUED")
                        .param("page", "0").param("size", "10")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].production.totalItems").value(2));
        mvc.perform(post("/api/staff/print/production/10/items/21/start")
                        .header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productionStatus").value("PRINTING"));
        mvc.perform(get("/api/staff/print/production/getId/10")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentQuoteId").value(55))
                .andExpect(jsonPath("$.data.shipping.shippingReady").value(false));
        mvc.perform(post("/api/staff/print/production/10/items/21/complete")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productionStatus").value("COMPLETED"));
        mvc.perform(get("/api/staff/print/production/getAll").header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/staff/print/production/10/items/21/start")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/print/production/getAll"))
                .andExpect(status().isUnauthorized());
        verify(production).board("QUEUED", 0, 10);
    }

    @Test
    void staffCorrectionAndApprovalValidateVersionsAndRole() throws Exception {
        when(staffReviews.requestCorrection(eq(7), eq(10), any())).thenReturn(
                new PrintRequestTechnicalReviewResponse(20, "CORRECTION_REQUESTED",
                        1, 1, "Unsupported geometry", 7, Instant.now()));
        when(staffReviews.approve(eq(7), eq(10), any())).thenReturn(
                new PrintRequestTechnicalReviewResponse(21, "APPROVED",
                        2, 1, null, 7, Instant.now()));
        mvc.perform(post("/api/staff/print/requests/10/request-correction")
                        .header("Authorization", token("PRINT_STAFF"))
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":1,\"expectedSpecVersion\":1,"
                                + "\"reason\":\"Unsupported geometry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("CORRECTION_REQUESTED"));
        mvc.perform(post("/api/staff/print/requests/10/approve-review")
                        .header("Authorization", token("ADMIN"))
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":2,\"expectedSpecVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/staff/print/requests/10/request-correction")
                        .header("Authorization", token("PRINT_STAFF"))
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":1,\"expectedSpecVersion\":1,"
                                + "\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/staff/print/requests/10/request-correction")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":1,\"expectedSpecVersion\":1,"
                                + "\"reason\":\"Unsupported geometry\"}"))
                .andExpect(status().isForbidden());
        verify(staffReviews).requestCorrection(eq(7), eq(10), any());
        verify(staffReviews).approve(eq(7), eq(10), any());
    }

    @Test
    void staffDownloadOnlyUsesScopedPrintRequestFileService() throws Exception {
        when(staffAccess.openCurrentFile(10)).thenReturn(new AuthorizedModelDownload(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "print-request-10-file-v1.stl",
                "model/stl", 3L));
        mvc.perform(get("/api/staff/print/requests/getId/10/file/download")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
        mvc.perform(get("/api/staff/print/requests/getId/10/file/download")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        verify(staffAccess).openCurrentFile(10);
    }

    @Test
    void staffAndCustomerQuoteAndRevisionRoutesEnforceRoleAndUpfrontContract() throws Exception {
        var line = new PrintQuoteItemResponse(1, 21, 2, 1,
                JsonMapper.builder().build().readTree("{\"materialId\":3}"), 2,
                new BigDecimal("100.00"), new BigDecimal("50.00"),
                new BigDecimal("0.00"), new BigDecimal("150.00"));
        var quoteResponse = new PrintQuoteResponse(55, 10, 1, 2, 1,
                new BigDecimal("150.00"), new BigDecimal("150.00"), new BigDecimal("0.00"),
                null, Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-26T00:00:00Z"), null, "ISSUED", true, 0, 3,
                List.of(line));
        when(quotes.issueQuote(eq(7), eq(10), any())).thenReturn(quoteResponse);
        when(quotes.listStaffQuotes(10)).thenReturn(List.of(quoteResponse));
        when(quotes.listCustomerQuotes(7, 10)).thenReturn(List.of(quoteResponse));
        when(quotes.acceptQuote(7, 10, 55)).thenReturn(new PrintQuoteResponse(
                55, 10, 1, 2, 1, new BigDecimal("150.00"), new BigDecimal("150.00"),
                new BigDecimal("0.00"), null, Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-26T00:00:00Z"), Instant.parse("2026-10-25T01:00:00Z"),
                "ACCEPTED", true, 0, 3, List.of(line)));
        when(quotes.requestRevision(eq(7), eq(10), eq(55), any())).thenReturn(
                new PrintQuoteResponse(55, 10, 1, 2, 1, new BigDecimal("150.00"),
                        new BigDecimal("150.00"), new BigDecimal("0.00"), null,
                        Instant.parse("2026-10-25T00:00:00Z"),
                        Instant.parse("2026-10-26T00:00:00Z"), null, "SUPERSEDED",
                        false, 1, 2, List.of(line)));

        String createQuoteBody = "{\"expectedFileVersion\":2,\"expectedSpecVersion\":1,"
                + "\"items\":[{\"printRequestItemId\":21,\"materialCostSnapshot\":100.00,"
                + "\"laborCostSnapshot\":50.00}]}";
        mvc.perform(post("/api/staff/print/requests/10/quotes/create")
                        .header("Authorization", token("PRINT_STAFF"))
                        .contentType("application/json")
                        .content(createQuoteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.printQuoteId").value(55))
                .andExpect(jsonPath("$.data.totalAmount").value(150.00))
                .andExpect(jsonPath("$.data.depositAmount").value(150.00))
                .andExpect(jsonPath("$.data.remainingAmount").value(0.00));
        mvc.perform(post("/api/staff/print/requests/10/quotes/create")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content(createQuoteBody))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/print/requests/getId/10/quotes/getAll")
                        .header("Authorization", token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].printQuoteId").value(55));
        mvc.perform(get("/api/print/requests/getId/10/quotes/getAll")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].remainingRevisions").value(3));
        mvc.perform(post("/api/print/requests/getId/10/quotes/55/accept")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quoteStatus").value("ACCEPTED"));
        mvc.perform(post("/api/print/requests/getId/10/quotes/55/request-revision")
                        .header("Authorization", customerToken)
                        .contentType("application/json")
                        .content("{\"expectedFileVersion\":2,\"expectedSpecVersion\":1,"
                                + "\"reason\":\"Adjust infill and wall thickness\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quoteStatus").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data.postQuoteRevisionsUsed").value(1))
                .andExpect(jsonPath("$.data.remainingRevisions").value(2));
        verify(quotes).issueQuote(eq(7), eq(10), any());
        verify(quotes).listStaffQuotes(10);
        verify(quotes).listCustomerQuotes(7, 10);
        verify(quotes).acceptQuote(7, 10, 55);
        verify(quotes).requestRevision(eq(7), eq(10), eq(55), any());
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
        mvc.perform(get("/api/staff/print/requests")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/print/requests/10")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/print/requests/10/file/download")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/print/requests/10/quotes")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/print/production")
                        .header("Authorization", token("PRINT_STAFF")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/staff/print/production/10")
                        .header("Authorization", token("PRINT_STAFF")))
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
    @Import({SecurityConfig.class, PrintRequestController.class, StaffPrintRequestController.class,
            StaffPrintProductionController.class,
            PrintRequestExceptionHandler.class, StaffPrintRequestExceptionHandler.class,
            GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean PrintRequestService printRequestService() { return mock(PrintRequestService.class); }
        @Bean PrintRequestFileCreateService printRequestFileCreateService() { return mock(PrintRequestFileCreateService.class); }
        @Bean PrintRequestFileDownloadService printRequestFileDownloadService() { return mock(PrintRequestFileDownloadService.class); }
        @Bean PrintRequestFileVersionService printRequestFileVersionService() { return mock(PrintRequestFileVersionService.class); }
        @Bean PrintRequestSpecificationService printRequestSpecificationService() {
            return mock(PrintRequestSpecificationService.class);
        }
        @Bean PrintRequestStateService printRequestStateService() {
            return mock(PrintRequestStateService.class);
        }
        @Bean PrintRequestStaffAccessService printRequestStaffAccessService() {
            return mock(PrintRequestStaffAccessService.class);
        }
        @Bean PrintRequestStaffQueueService printRequestStaffQueueService() {
            return mock(PrintRequestStaffQueueService.class);
        }
        @Bean PrintRequestProductionManagementService printRequestProductionManagementService() {
            return mock(PrintRequestProductionManagementService.class);
        }
        @Bean PrintRequestTechnicalReviewService printRequestTechnicalReviewService() {
            return mock(PrintRequestTechnicalReviewService.class);
        }
        @Bean PrintRequestQuoteService printRequestQuoteService() {
            return mock(PrintRequestQuoteService.class);
        }
        @Bean PrintRequestWorkflowService printRequestWorkflowService() {
            return mock(PrintRequestWorkflowService.class);
        }
        @Bean PrintRequestShippingAddressService printRequestShippingAddressService() {
            return mock(PrintRequestShippingAddressService.class);
        }
        @Bean PrintRequestShippingReadinessService printRequestShippingReadinessService() {
            return mock(PrintRequestShippingReadinessService.class);
        }
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
