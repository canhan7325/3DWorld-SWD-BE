package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

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

    @BeforeEach
    void setUp() {
        requests = mock(PrintRequestRepository.class);
        items = mock(PrintRequestItemRepository.class);
        service = new PrintRequestServiceImpl(requests, items);
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
