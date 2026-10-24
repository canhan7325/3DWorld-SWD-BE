package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.dto.request.ConfirmPrintRequestPreviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CancelPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.SubmitPrintRequestRequest;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStateService;
import com.project.w_3d_world.features.printing.validation.PrintRequestSubmissionValidation;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestServiceTest {
    private PrintRequestRepository requests;
    private PrintRequestItemRepository items;
    private PrintRequestServiceImpl service;
    private PrintRequestStateService states;
    private PrintRequestFileVersionRepository fileVersions;
    private PrintRequestSpecVersionRepository specVersions;
    private PrintRequestStatusHistoryRepository histories;
    private PrintRequestSubmissionValidation submissionValidation;

    @BeforeEach
    void setUp() {
        requests = mock(PrintRequestRepository.class);
        items = mock(PrintRequestItemRepository.class);
        fileVersions = mock(PrintRequestFileVersionRepository.class);
        specVersions = mock(PrintRequestSpecVersionRepository.class);
        histories = mock(PrintRequestStatusHistoryRepository.class);
        submissionValidation = mock(PrintRequestSubmissionValidation.class);
        service = new PrintRequestServiceImpl(requests, items,
                mock(PrintMaterialRepository.class), mock(ModelVersionRepository.class),
                mock(Model3DRepository.class), mock(ModelFileRepository.class),
                mock(AuditLogRepository.class), JsonMapper.builder().build(),
                histories);
        states = new PrintRequestStateService(requests, fileVersions, specVersions,
                histories, items, submissionValidation);
    }

    @Test
    void previewConfirmationRequiresOwnedCurrentFileAndSpec() {
        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(3);
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        when(fileVersions.findByPrintRequestIdAndVersion(10, 2))
                .thenReturn(Optional.of(mock(PrintRequestFileVersion.class)));
        when(specVersions.findByPrintRequestIdAndVersion(10, 3))
                .thenReturn(Optional.of(mock(PrintRequestSpecVersion.class)));

        var result = states.confirmPreview(7, 10, new ConfirmPrintRequestPreviewRequest(2, 3));

        assertTrue(result.previewConfirmed());
        assertEquals(2, result.currentFileVersion());
        assertEquals(3, result.currentSpecVersion());
        assertNotNull(result.updatedAt());
        verify(requests).saveAndFlush(request);
        request.useFileVersion(4);
        assertFalse(request.isPreviewConfirmed());
        request.confirmPreview();
        request.useSpecVersion(5);
        assertFalse(request.isPreviewConfirmed());
    }

    @Test
    void previewConfirmationRejectsOtherOwnerAndStaleOrMissingVersions() {
        var expected = new ConfirmPrintRequestPreviewRequest(2, 3);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> states.confirmPreview(7, 10, expected)).getStatus());
        verify(requests).findOwnedByIdForUpdate(10, 7);
        verify(requests, never()).saveAndFlush(any());

        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(3);
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.confirmPreview(7, 10,
                        new ConfirmPrintRequestPreviewRequest(1, 3))).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.confirmPreview(7, 10, expected)).getStatus());
        assertFalse(request.isPreviewConfirmed());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void statusHistoryIsOwnerScopedAndOrderedByTimestampThenId() {
        PrintRequest request = request(10, 7);
        when(requests.findByPrintRequestIdAndCustomerUserId(10, 7))
                .thenReturn(Optional.of(request));
        var initial = new PrintRequestStatusHistory(10, null, "DRAFT", 7, null, null);
        ReflectionTestUtils.setField(initial, "printStatusHistoryId", 1);
        when(histories.findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(10))
                .thenReturn(List.of(initial));

        var result = states.history(7, 10);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().historyId());
        assertEquals("DRAFT", result.getFirst().toStatus());
        assertEquals(7, result.getFirst().changedByUserId());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> states.history(8, 10)).getStatus());
        verify(histories, times(1))
                .findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(10);
    }

    @Test
    void submitRequiresCurrentVersionsAndBothConfirmationsAndWritesHistoryOnce() {
        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(3);
        request.confirmCopyrightDeclaration();
        request.confirmPreview();
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        when(fileVersions.findByPrintRequestIdAndVersion(10, 2))
                .thenReturn(Optional.of(mock(PrintRequestFileVersion.class)));
        when(specVersions.findByPrintRequestIdAndVersion(10, 3))
                .thenReturn(Optional.of(mock(PrintRequestSpecVersion.class)));
        var input = new SubmitPrintRequestRequest(2, 3);

        var result = states.submit(7, 10, input);

        assertEquals("SUBMITTED", result.status());
        assertNotNull(result.submittedAt());
        assertEquals(result.submittedAt(), result.updatedAt());
        verify(submissionValidation).validate(eq(request), any(), anyList());
        verify(requests).saveAndFlush(request);
        var saved = ArgumentCaptor.forClass(PrintRequestStatusHistory.class);
        verify(histories).saveAndFlush(saved.capture());
        assertEquals("DRAFT", saved.getValue().getFromStatus());
        assertEquals("SUBMITTED", saved.getValue().getToStatus());
        assertEquals(7, saved.getValue().getChangedByUserId());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.submit(7, 10, input)).getStatus());
        verify(histories, times(1)).saveAndFlush(any());
    }

    @Test
    void submitRejectsUnconfirmedAndStaleDraftWithoutWrites() {
        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(3);
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.submit(7, 10, new SubmitPrintRequestRequest(1, 3))).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.submit(7, 10, new SubmitPrintRequestRequest(2, 3))).getStatus());
        assertEquals("DRAFT", request.getStatus());
        assertNull(request.getSubmittedAt());
        verifyNoInteractions(histories, submissionValidation);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void cancelRecordsActualTransitionAndRejectsReplay() {
        PrintRequest request = request(10, 7);
        request.submit();
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));

        var result = states.cancel(7, 10, new CancelPrintRequestRequest("No longer needed"));

        assertEquals("CANCELLED", result.status());
        assertNotNull(result.cancelledAt());
        assertEquals("No longer needed", request.getCancellationReason());
        var saved = ArgumentCaptor.forClass(PrintRequestStatusHistory.class);
        verify(histories).saveAndFlush(saved.capture());
        assertEquals("SUBMITTED", saved.getValue().getFromStatus());
        assertEquals("CANCELLED", saved.getValue().getToStatus());
        assertEquals("No longer needed", saved.getValue().getReason());
        assertEquals(7, saved.getValue().getChangedByUserId());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.cancel(7, 10, new CancelPrintRequestRequest("Again"))).getStatus());
        verify(histories, times(1)).saveAndFlush(any());
    }

    @Test
    void cancelDraftUsesDraftAsHistorySourceAndMutationsUseRepositoryLock() throws Exception {
        PrintRequest request = request(10, 7);
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));

        states.cancel(7, 10, new CancelPrintRequestRequest("Changed plans"));

        var saved = ArgumentCaptor.forClass(PrintRequestStatusHistory.class);
        verify(histories).saveAndFlush(saved.capture());
        assertEquals("DRAFT", saved.getValue().getFromStatus());
        assertEquals("CANCELLED", saved.getValue().getToStatus());
        assertEquals(LockModeType.PESSIMISTIC_WRITE,
                PrintRequestRepository.class.getMethod("findOwnedByIdForUpdate",
                        Integer.class, Integer.class).getAnnotation(Lock.class).value());
        for (var method : List.of("confirmPreview", "submit", "cancel")) {
            var inputType = switch (method) {
                case "confirmPreview" -> ConfirmPrintRequestPreviewRequest.class;
                case "submit" -> SubmitPrintRequestRequest.class;
                default -> CancelPrintRequestRequest.class;
            };
            assertFalse(PrintRequestStateService.class.getMethod(method,
                    Integer.class, Integer.class, inputType)
                    .getAnnotation(Transactional.class).readOnly());
        }
    }

    @Test
    void cancelRejectsMissingOwnerInvalidReasonAndLockedRequest() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> states.cancel(7, 10,
                        new CancelPrintRequestRequest("No longer needed"))).getStatus());
        PrintRequest request = request(10, 7);
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> states.cancel(7, 10, new CancelPrintRequestRequest(" "))).getStatus());
        ReflectionTestUtils.setField(request, "lockedAt", Instant.now());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.cancel(7, 10,
                        new CancelPrintRequestRequest("No longer needed"))).getStatus());
        verifyNoInteractions(histories);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void listUsesOwnerScopedQueryPaginationAndStableSort() {
        when(requests.findByCustomerUserId(eq(7), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(request(10, 7)), call.getArgument(1), 21));

        var response = service.getMyRequests(7, 1, 10);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(requests).findByCustomerUserId(eq(7), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("printRequestId")),
                pageable.getValue().getSort());
        assertEquals(10, response.content().getFirst().printRequestId());
        assertEquals("DRAFT", response.content().getFirst().status());
        assertEquals(21, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals(1, response.page());
        assertEquals(10, response.size());
        assertFalse(response.content().getFirst().toString().contains("customerUserId"));
        verifyNoInteractions(items);
    }

    @Test
    void emptyListReturnsEmptyPageWithoutItemLookup() {
        when(requests.findByCustomerUserId(eq(7), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<PrintRequest>(List.of(), call.getArgument(1), 0));
        var response = service.getMyRequests(7, 0, 20);
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        verifyNoInteractions(items);
    }

    @Test
    void invalidListInputsNeverReachRepositories() {
        assertThrows(ApiException.class, () -> service.getMyRequests(null, 0, 20));
        assertThrows(ApiException.class, () -> service.getMyRequests(0, 0, 20));
        assertThrows(ApiException.class, () -> service.getMyRequests(7, -1, 20));
        assertThrows(ApiException.class, () -> service.getMyRequests(7, 0, 0));
        assertThrows(ApiException.class, () -> service.getMyRequests(7, 0, 51));
        verifyNoInteractions(requests, items);
    }

    @Test
    void detailChecksOwnershipBeforeLoadingItemsAndKeepsHistoricalMaterial() {
        when(requests.findByPrintRequestIdAndCustomerUserId(10, 7))
                .thenReturn(Optional.of(request(10, 7)));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(item(21, 10, 4, null, "private/file.glb"),
                        item(22, 10, 5, 31, null)));

        var response = service.getMyRequestById(7, 10);

        assertEquals(10, response.printRequestId());
        assertEquals(2, response.items().size());
        assertEquals(4, response.items().getFirst().materialId());
        assertEquals("UPLOADED_FILE", response.items().getFirst().sourceType());
        assertNull(response.items().getFirst().modelVersionId());
        assertEquals("MODEL_VERSION", response.items().get(1).sourceType());
        assertEquals(31, response.items().get(1).modelVersionId());
        assertFalse(response.toString().contains("private/file.glb"));
        assertFalse(response.toString().contains("estimatedUnitCost"));
        var order = inOrder(requests, items);
        order.verify(requests).findByPrintRequestIdAndCustomerUserId(10, 7);
        order.verify(items).findByPrintRequestIdOrderByPrintRequestItemIdAsc(10);
        verifyNoMoreInteractions(requests, items);
    }

    @Test
    void missingAndForeignRequestsBothReturnNonDisclosing404BeforeItems() {
        when(requests.findByPrintRequestIdAndCustomerUserId(99, 7)).thenReturn(Optional.empty());
        when(requests.findByPrintRequestIdAndCustomerUserId(10, 8)).thenReturn(Optional.empty());
        var missing = assertThrows(ApiException.class, () -> service.getMyRequestById(7, 99));
        var foreign = assertThrows(ApiException.class, () -> service.getMyRequestById(8, 10));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
        assertEquals(missing.getMessage(), foreign.getMessage());
        verify(requests).findByPrintRequestIdAndCustomerUserId(99, 7);
        verify(requests).findByPrintRequestIdAndCustomerUserId(10, 8);
        verifyNoInteractions(items);
    }

    @Test
    void invalidDetailInputsNeverReachRepositories() {
        assertThrows(ApiException.class, () -> service.getMyRequestById(null, 10));
        assertThrows(ApiException.class, () -> service.getMyRequestById(7, null));
        assertThrows(ApiException.class, () -> service.getMyRequestById(7, 0));
        verifyNoInteractions(requests, items);
    }

    @Test
    void serviceIsReadOnly() {
        var transaction = PrintRequestServiceImpl.class.getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(transaction.readOnly());
    }

    private static PrintRequest request(int id, int owner) {
        var request = new PrintRequestForTest();
        ReflectionTestUtils.setField(request, "printRequestId", id);
        ReflectionTestUtils.setField(request, "customerUserId", owner);
        ReflectionTestUtils.setField(request, "status", "DRAFT");
        ReflectionTestUtils.setField(request, "customerNotes", "Prototype");
        ReflectionTestUtils.setField(request, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        return request;
    }

    private static PrintRequestItem item(int id, int requestId, int materialId,
            Integer versionId, String storageKey) {
        var item = new PrintRequestItemForTest();
        ReflectionTestUtils.setField(item, "printRequestItemId", id);
        ReflectionTestUtils.setField(item, "printRequestId", requestId);
        ReflectionTestUtils.setField(item, "materialId", materialId);
        ReflectionTestUtils.setField(item, "modelVersionId", versionId);
        ReflectionTestUtils.setField(item, "uploadedFileKey", storageKey);
        ReflectionTestUtils.setField(item, "quantity", 2);
        ReflectionTestUtils.setField(item, "widthMm", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(item, "productionStatus", "PENDING");
        return item;
    }

    private static final class PrintRequestForTest extends PrintRequest {}
    private static final class PrintRequestItemForTest extends PrintRequestItem {}
}
