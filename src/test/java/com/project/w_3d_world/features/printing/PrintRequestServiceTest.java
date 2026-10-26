package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentAttemptRepository;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintQuoteItem;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.entity.PrintRequestTechnicalReview;
import com.project.w_3d_world.features.printing.dto.request.ApprovePrintRequestReviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintCorrectionRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintQuoteRevisionRequest;
import com.project.w_3d_world.features.printing.dto.request.ConfirmPrintRequestPreviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CancelPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.SubmitPrintRequestRequest;
import com.project.w_3d_world.features.printing.exception.QuoteExpiredException;
import com.project.w_3d_world.features.printing.repository.PrintQuoteItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestTechnicalReviewRepository;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestProductionService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestProductionManagementService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingAddressService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingReadinessService;
import com.project.w_3d_world.features.user.entity.UserAddress;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestPaymentFulfillmentService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestPaymentFulfillmentWorker;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestQuoteService;
import com.project.w_3d_world.features.printing.service.impl.PrintQuoteExpirationWorker;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffAccessService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffQueueService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestTechnicalReviewService;
import com.project.w_3d_world.features.printing.validation.PrintRequestSubmissionValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestWorkflowPolicy;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestServiceImpl;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintRequestServiceTest {
    @Test
    void draftSelectsOwnedAddressSnapshotAndFreezesItAfterSubmit() {
        var requestRepo = mock(PrintRequestRepository.class);
        var addressRepo = mock(UserAddressRepository.class);
        var audit = mock(AuditLogRepository.class);
        var shipping = new PrintRequestShippingAddressService(requestRepo, addressRepo,
                audit, JsonMapper.builder().build());
        var request = request(10, 7);
        var address = new UserAddress(7, "Buyer", "0900000000", "1 Main St", "HCMC");
        address.updateRegion("HCMC", "District 1", "Ward 1");
        ReflectionTestUtils.setField(address, "id", 3);
        when(requestRepo.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        when(addressRepo.findById(3)).thenReturn(Optional.of(address));

        shipping.select(7, 10, 3);
        shipping.select(7, 10, 3);

        assertEquals(3, request.getShippingAddressId());
        assertTrue(request.getShippingAddressSnapshot().contains("1 Main St"));
        verify(requestRepo).saveAndFlush(request);
        verify(audit).saveAndFlush(any());
        request.submit();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> shipping.select(7, 10, 3)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> shipping.select(8, 10, 3)).getStatus());
    }

    @Test
    void shippingAddressSelectionRejectsAnotherCustomersAddressAndIncompleteAddress() {
        var requestRepo = mock(PrintRequestRepository.class);
        var addressRepo = mock(UserAddressRepository.class);
        var audit = mock(AuditLogRepository.class);
        var shipping = new PrintRequestShippingAddressService(requestRepo, addressRepo,
                audit, JsonMapper.builder().build());
        var request = request(10, 7);
        when(requestRepo.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        when(addressRepo.findById(3)).thenReturn(Optional.of(
                new UserAddress(8, "Other", "0900000000", "1 Main St", "HCMC")));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> shipping.select(7, 10, 3)).getStatus());
        when(addressRepo.findById(3)).thenReturn(Optional.of(
                new UserAddress(7, "Buyer", null, "1 Main St", "HCMC")));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> shipping.select(7, 10, 3)).getStatus());
        verify(requestRepo, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void shippingReadinessRequiresAllItemsAddressTrustedQueueAndNoShipment() {
        var requestRepo = mock(PrintRequestRepository.class);
        var itemRepo = mock(PrintRequestItemRepository.class);
        var quoteRepo = mock(PrintQuoteRepository.class);
        var historyRepo = mock(PrintRequestStatusHistoryRepository.class);
        var entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked")
        TypedQuery<Long> query = mock(TypedQuery.class);
        var shipping = new PrintRequestShippingReadinessService(requestRepo, itemRepo,
                quoteRepo, historyRepo, entityManager, JsonMapper.builder().build());
        var request = acceptedRequest(10, 7, 55);
        request.lockForProduction(Instant.now());
        request.selectShippingAddress(3, "{\"recipientName\":\"Buyer\",\"phone\":\"0900000000\","
                + "\"addressLine\":\"1 Main St\",\"province\":\"HCMC\","
                + "\"district\":\"District 1\",\"ward\":\"Ward 1\"}");
        var quote = PrintQuote.issueUpfront(10, 9, 1, 2, 1,
                new BigDecimal("100.00"), null, Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(quote, "printQuoteId", 55);
        quote.accept(Instant.now());
        var first = item(21, 10, 4, 12, null);
        first.queueForProduction();
        first.startPrinting();
        first.completePrinting();
        var second = item(22, 10, 4, 12, null);
        second.queueForProduction();
        second.startPrinting();
        second.completePrinting();
        when(requestRepo.findByPrintRequestIdAndCustomerUserId(10, 7)).thenReturn(Optional.of(request));
        when(requestRepo.findById(10)).thenReturn(Optional.of(request));
        when(itemRepo.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(first, second));
        when(quoteRepo.findByPrintQuoteIdAndPrintRequestId(55, 10)).thenReturn(Optional.of(quote));
        when(historyRepo.existsByPrintRequestIdAndToStatusAndSourceEventIdStartingWith(
                10, "IN_QUEUE", "POD_PAYMENT_VERIFIED:10:tx:")).thenReturn(true);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter("requestId", 10)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        var ready = shipping.forCustomer(7, 10);
        assertTrue(ready.shippingReady());
        assertEquals(2, ready.completedItems());
        assertEquals(2, ready.totalItems());
        assertFalse(ready.shipmentExists());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> shipping.forCustomer(8, 10)).getStatus());

        ReflectionTestUtils.setField(second, "productionStatus", "PRINTING");
        assertTrue(shipping.forStaff(10).shippingBlockers().contains("PRODUCTION_ITEMS_INCOMPLETE"));
        ReflectionTestUtils.setField(second, "productionStatus", "QUEUED");
        assertFalse(shipping.forStaff(10).shippingReady());
        when(itemRepo.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10)).thenReturn(List.of());
        assertTrue(shipping.forStaff(10).shippingBlockers().contains("PRODUCTION_ITEMS_MISSING"));
        when(itemRepo.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(first, second));
        ReflectionTestUtils.setField(second, "productionStatus", "COMPLETED");
        when(query.getSingleResult()).thenReturn(1L);
        assertTrue(shipping.forStaff(10).shippingBlockers().contains("POD_SHIPMENT_ALREADY_EXISTS"));
        when(query.getSingleResult()).thenReturn(0L);
        ReflectionTestUtils.setField(quote, "specificationVersionSnapshot", 99);
        assertTrue(shipping.forStaff(10).shippingBlockers()
                .contains("CURRENT_ACCEPTED_FULL_QUOTE_MISSING"));
        ReflectionTestUtils.setField(quote, "specificationVersionSnapshot", 1);
        ReflectionTestUtils.setField(quote, "acceptedAt", null);
        assertTrue(shipping.forStaff(10).shippingBlockers()
                .contains("CURRENT_ACCEPTED_FULL_QUOTE_MISSING"));
        assertEquals("IN_QUEUE", request.getStatus());
        verify(itemRepo, never()).saveAndFlush(any());
    }

    @Test
    void shippingReadinessFailsClosedOnMissingQueueQuoteOrAddress() {
        var requestRepo = mock(PrintRequestRepository.class);
        var itemRepo = mock(PrintRequestItemRepository.class);
        var quoteRepo = mock(PrintQuoteRepository.class);
        var historyRepo = mock(PrintRequestStatusHistoryRepository.class);
        var entityManager = mock(EntityManager.class);
        @SuppressWarnings("unchecked") TypedQuery<Long> query = mock(TypedQuery.class);
        var shipping = new PrintRequestShippingReadinessService(requestRepo, itemRepo,
                quoteRepo, historyRepo, entityManager, JsonMapper.builder().build());
        var request = acceptedRequest(10, 7, 55);
        request.lockForProduction(Instant.now());
        var item = item(21, 10, 4, 12, null);
        item.queueForProduction();
        item.startPrinting();
        item.completePrinting();
        when(requestRepo.findById(10)).thenReturn(Optional.of(request));
        when(itemRepo.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(item));
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter("requestId", 10)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        var result = shipping.forStaff(10);
        assertFalse(result.shippingReady());
        assertTrue(result.shippingBlockers().contains("VERIFIED_PAYMENT_QUEUE_EVENT_MISSING"));
        assertTrue(result.shippingBlockers().contains("CURRENT_ACCEPTED_FULL_QUOTE_MISSING"));
        assertTrue(result.shippingBlockers().contains("SHIPPING_ADDRESS_MISSING"));
        request.cancel("cancelled");
        assertTrue(shipping.forStaff(10).shippingBlockers().contains("NOT_IN_PRODUCTION_QUEUE"));
        verify(itemRepo, never()).saveAndFlush(any());
    }

    @Test
    void productionBoardPaginatesByQueueTimeAndBatchesItemStates() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var production = new PrintRequestProductionManagementService(requests, items,
                mock(PrintQuoteRepository.class), mock(PrintQuoteItemRepository.class),
                mock(PrintRequestStatusHistoryRepository.class), mock(AuditLogRepository.class),
                JsonMapper.builder().build());
        var request = acceptedRequest(10, 7, 55);
        request.lockForProduction(Instant.parse("2026-10-26T10:00:00Z"));
        var queued = item(21, 10, 4, 12, null);
        queued.queueForProduction();
        var completed = item(22, 10, 4, 12, null);
        completed.queueForProduction();
        completed.startPrinting();
        completed.completePrinting();
        when(requests.findProductionBoard(eq("QUEUED"), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(request), call.getArgument(1), 11));
        when(items.findByPrintRequestIdInOrderByPrintRequestIdAscPrintRequestItemIdAsc(List.of(10)))
                .thenReturn(List.of(queued, completed));

        var page = production.board("QUEUED", 1, 10);

        assertEquals(11, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().getFirst().production().totalItems());
        assertEquals(1, page.content().getFirst().production().completedItems());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(requests).findProductionBoard(eq("QUEUED"), pageable.capture());
        assertEquals(Sort.by(Sort.Order.asc("lockedAt"), Sort.Order.asc("printRequestId")),
                pageable.getValue().getSort());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> production.board("MACHINE_RUNNING", 0, 10)).getStatus());
    }

    @Test
    void productionDetailReturnsLockedQuoteSnapshotsWithoutPrivateFileReference() {
        var requests = mock(PrintRequestRepository.class);
        var items = mock(PrintRequestItemRepository.class);
        var quotes = mock(PrintQuoteRepository.class);
        var quoteItems = mock(PrintQuoteItemRepository.class);
        var histories = mock(PrintRequestStatusHistoryRepository.class);
        var production = new PrintRequestProductionManagementService(requests, items,
                quotes, quoteItems, histories, mock(AuditLogRepository.class),
                JsonMapper.builder().build());
        var request = acceptedRequest(10, 7, 55);
        request.lockForProduction(Instant.now());
        var quote = PrintQuote.issueUpfront(10, 9, 1, 2, 1,
                new BigDecimal("100.00"), null, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(quote, "printQuoteId", 55);
        quote.accept(Instant.now());
        var item = item(21, 10, 4, 12, null);
        item.queueForProduction();
        var line = new PrintQuoteItem(55, 21, 2, 1, "/private/model/file",
                "{\"materialId\":4}", 2, new BigDecimal("80.00"),
                new BigDecimal("20.00"), BigDecimal.ZERO, new BigDecimal("100.00"));
        when(requests.findById(10)).thenReturn(Optional.of(request));
        when(histories.existsByPrintRequestIdAndToStatusAndSourceEventIdStartingWith(
                10, "IN_QUEUE", "POD_PAYMENT_VERIFIED:10:tx:")).thenReturn(true);
        when(quotes.findByPrintQuoteIdAndPrintRequestId(55, 10)).thenReturn(Optional.of(quote));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10)).thenReturn(List.of(item));
        when(quoteItems.findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(55)).thenReturn(List.of(line));

        var detail = production.detail(10);

        assertEquals("IN_QUEUE", detail.status());
        assertEquals(55, detail.currentQuoteId());
        assertEquals("QUEUED", detail.items().getFirst().item().productionStatus());
        assertTrue(detail.items().getFirst().canStart());
        assertFalse(detail.items().getFirst().canComplete());
        assertEquals(new BigDecimal("100.00"), detail.quoteItems().getFirst().lineTotal());
        assertFalse(detail.toString().contains("/private/model/file"));
    }

    @Test
    void productionStartCompleteAreItemScopedIdempotentAndAudited() {
        var quoteRepo = mock(PrintQuoteRepository.class);
        var audit = mock(AuditLogRepository.class);
        var production = new PrintRequestProductionManagementService(requests, items,
                quoteRepo, mock(PrintQuoteItemRepository.class), histories, audit,
                JsonMapper.builder().build());
        var request = acceptedRequest(10, 7, 55);
        request.lockForProduction(Instant.now());
        var quote = PrintQuote.issueUpfront(10, 9, 1, 2, 1,
                new BigDecimal("100.00"), null, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(quote, "printQuoteId", 55);
        quote.accept(Instant.now());
        var first = item(21, 10, 4, 12, null);
        first.queueForProduction();
        var second = item(22, 10, 4, 12, null);
        second.queueForProduction();
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(histories.existsByPrintRequestIdAndToStatusAndSourceEventIdStartingWith(
                10, "IN_QUEUE", "POD_PAYMENT_VERIFIED:10:tx:")).thenReturn(true);
        when(quoteRepo.findByPrintQuoteIdAndPrintRequestId(55, 10)).thenReturn(Optional.of(quote));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10)).thenReturn(List.of(first, second));

        assertEquals("PRINTING", production.start(9, 10, 21).productionStatus());
        production.start(9, 10, 21);
        assertEquals("COMPLETED", production.complete(9, 10, 21).productionStatus());
        production.complete(9, 10, 21);

        assertEquals("IN_QUEUE", request.getStatus());
        assertEquals("QUEUED", second.getProductionStatus());
        verify(items, times(2)).saveAndFlush(first);
        var events = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit, times(2)).saveAndFlush(events.capture());
        assertEquals("POD_ITEM_PRINTING_STARTED", events.getAllValues().get(0).getActionType());
        assertEquals("POD_ITEM_PRINTING_COMPLETED", events.getAllValues().get(1).getActionType());
        assertEquals(9, events.getAllValues().get(0).getActorUserId());
        assertTrue(events.getAllValues().get(0).getMetadata().contains("\"printRequestItemId\":21"));
        verify(histories, never()).saveAndFlush(any());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.start(9, 10, 21)).getStatus());
    }

    @Test
    void productionMutationRequiresTrustedQueueAndExactRequestItem() {
        var quoteRepo = mock(PrintQuoteRepository.class);
        var audit = mock(AuditLogRepository.class);
        var production = new PrintRequestProductionManagementService(requests, items,
                quoteRepo, mock(PrintQuoteItemRepository.class), histories, audit,
                JsonMapper.builder().build());
        var request = acceptedRequest(10, 7, 55);
        var foreign = item(21, 11, 4, 12, null);
        foreign.queueForProduction();
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.start(9, 10, 21)).getStatus());
        request.lockForProduction(Instant.now());
        when(histories.existsByPrintRequestIdAndToStatusAndSourceEventIdStartingWith(
                10, "IN_QUEUE", "POD_PAYMENT_VERIFIED:10:tx:")).thenReturn(true);
        var quote = PrintQuote.issueUpfront(10, 9, 1, 2, 1,
                new BigDecimal("100.00"), null, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(quote, "printQuoteId", 55);
        quote.accept(Instant.now());
        when(quoteRepo.findByPrintQuoteIdAndPrintRequestId(55, 10)).thenReturn(Optional.of(quote));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10)).thenReturn(List.of(foreign));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> production.start(9, 10, 21)).getStatus());
        request.cancel("cancelled");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.complete(9, 10, 21)).getStatus());
        verify(items, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void customerDetailDerivesItemRowProgressWithoutChangingExistingFields() {
        var request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(1);
        request.submit();
        var first = item(21, 10, 4, 12, null);
        first.queueForProduction();
        first.startPrinting();
        first.completePrinting();
        var second = item(22, 10, 4, 12, null);
        second.queueForProduction();
        when(requests.findByPrintRequestIdAndCustomerUserId(10, 7))
                .thenReturn(Optional.of(request));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(first, second));

        var response = service.getMyRequestById(7, 10);

        assertEquals("SUBMITTED", response.status());
        assertEquals(2, response.production().totalItems());
        assertEquals(1, response.production().completedItems());
        assertEquals(1, response.production().queuedItems());
        assertEquals("PRINTING", response.production().productionDisplayStatus());
        assertEquals("COMPLETED", response.items().getFirst().productionStatus());
    }

    @Test
    void staffReviewQueuePaginatesAndBatchesLatestReviewWithoutCustomerData() {
        var requests = mock(PrintRequestRepository.class);
        var reviews = mock(PrintRequestTechnicalReviewRepository.class);
        var queue = new PrintRequestStaffQueueService(requests,
                mock(PrintRequestFileVersionRepository.class),
                mock(PrintRequestSpecVersionRepository.class), reviews,
                mock(AuditLogRepository.class), JsonMapper.builder().build());
        var unassigned = request(10, 7);
        unassigned.useFileVersion(2);
        unassigned.useSpecVersion(1);
        unassigned.submit();
        var assigned = request(11, 8);
        assigned.useFileVersion(3);
        assigned.useSpecVersion(2);
        assigned.submit();
        assigned.claimReview(9, Instant.now());
        when(requests.findStaffReviewQueue(eq("ALL"), eq(7), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(unassigned, assigned),
                        call.getArgument(2), 21));
        when(reviews.findByPrintRequestIdInOrderByCreatedAtDescTechnicalReviewIdDesc(List.of(10, 11)))
                .thenReturn(List.of(new PrintRequestTechnicalReview(11, 9,
                        "{\"outcome\":\"APPROVED\",\"reviewedFileVersion\":3,\"reviewedSpecVersion\":2}")));

        var page = queue.list(7, "ALL", 1, 10);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(requests).findStaffReviewQueue(eq("ALL"), eq(7), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Order.asc("submittedAt"), Sort.Order.asc("printRequestId")),
                pageable.getValue().getSort());
        assertEquals(21, page.totalElements());
        assertEquals(3, page.totalPages());
        assertTrue(page.content().getFirst().reviewEligible());
        assertFalse(page.content().getFirst().quoteEligible());
        assertNull(page.content().getFirst().assignedStaffId());
        assertFalse(page.content().getLast().reviewEligible());
        assertTrue(page.content().getLast().quoteEligible());
        assertFalse(page.content().getLast().canClaim());
        assertFalse(page.content().getFirst().toString().contains("customerUserId"));
        verify(reviews).findByPrintRequestIdInOrderByCreatedAtDescTechnicalReviewIdDesc(List.of(10, 11));
    }

    @Test
    void staffReviewQueueUsesPersistedAssignmentFilterAndRejectsInvalidOptions() {
        var repository = mock(PrintRequestRepository.class);
        var reviews = mock(PrintRequestTechnicalReviewRepository.class);
        var queue = new PrintRequestStaffQueueService(repository,
                mock(PrintRequestFileVersionRepository.class),
                mock(PrintRequestSpecVersionRepository.class), reviews,
                mock(AuditLogRepository.class), JsonMapper.builder().build());
        when(repository.findStaffReviewQueue(eq("UNASSIGNED"), eq(9), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<PrintRequest>(List.of(), call.getArgument(2), 0));

        var page = queue.list(9, "UNASSIGNED", 0, 20);

        assertTrue(page.content().isEmpty());
        assertEquals(0, page.totalElements());
        verify(repository).findStaffReviewQueue(eq("UNASSIGNED"), eq(9), any(Pageable.class));
        verifyNoInteractions(reviews);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> queue.list(9, "NOT_A_FILTER", 0, 20)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> queue.list(9, "ALL", 0, 51)).getStatus());
    }

    @Test
    void staffClaimIsIdempotentAuditedAndCompetingStaffConflicts() {
        var audit = mock(AuditLogRepository.class);
        var reviews = mock(PrintRequestTechnicalReviewRepository.class);
        var queue = new PrintRequestStaffQueueService(requests, fileVersions, specVersions,
                reviews, audit, JsonMapper.builder().build());
        var request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(1);
        request.submit();
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(fileVersions.findByPrintRequestIdAndVersion(10, 2))
                .thenReturn(Optional.of(mock(PrintRequestFileVersion.class)));
        when(specVersions.findByPrintRequestIdAndVersion(10, 1))
                .thenReturn(Optional.of(mock(PrintRequestSpecVersion.class)));

        var first = queue.claim(9, 10);
        var repeated = queue.claim(9, 10);

        assertEquals(9, first.assignedStaffId());
        assertEquals(first.handledAt(), repeated.handledAt());
        assertEquals("SUBMITTED", request.getStatus());
        assertEquals(2, request.getCurrentFileVersion());
        assertEquals(1, request.getCurrentSpecVersion());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> queue.claim(8, 10)).getStatus());
        verify(requests, times(1)).saveAndFlush(request);
        var event = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit).saveAndFlush(event.capture());
        assertEquals("POD_STAFF_REVIEW_CLAIMED", event.getValue().getActionType());
        assertEquals(9, event.getValue().getActorUserId());
        assertEquals(10, event.getValue().getEntityId());
        verifyNoInteractions(histories);
        try {
            var method = PrintRequestRepository.class.getMethod("findByIdForUpdate", Integer.class);
            assertEquals(LockModeType.PESSIMISTIC_WRITE, method.getAnnotation(Lock.class).value());
        } catch (NoSuchMethodException exception) {
            fail(exception);
        }
    }

    @Test
    void staffClaimRejectsCancelledQuotedAndLockedRequestsWithoutWrites() {
        var audit = mock(AuditLogRepository.class);
        var queue = new PrintRequestStaffQueueService(requests, fileVersions, specVersions,
                mock(PrintRequestTechnicalReviewRepository.class), audit,
                JsonMapper.builder().build());
        var cancelled = request(10, 7);
        cancelled.useFileVersion(1);
        cancelled.useSpecVersion(1);
        cancelled.submit();
        cancelled.cancel("No longer needed");
        var quoted = request(11, 7);
        quoted.useFileVersion(1);
        quoted.useSpecVersion(1);
        quoted.submit();
        quoted.markQuoted(55, 9, Instant.now());
        var locked = request(12, 7);
        locked.useFileVersion(1);
        locked.useSpecVersion(1);
        locked.submit();
        locked.lockForProduction(Instant.now());
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(cancelled));
        when(requests.findByIdForUpdate(11)).thenReturn(Optional.of(quoted));
        when(requests.findByIdForUpdate(12)).thenReturn(Optional.of(locked));

        for (int id : new int[]{10, 11, 12}) {
            assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                    () -> queue.claim(9, id)).getStatus());
        }
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(audit, histories);
    }

    @Test
    void quoteIssueKeepsExistingStaffAssignment() {
        var request = request(10, 7);
        Instant assignedAt = Instant.parse("2026-10-25T10:00:00Z");
        request.claimReview(9, assignedAt);

        request.markQuoted(55, 10, assignedAt.plusSeconds(600));

        assertEquals(9, request.getHandledByUserId());
        assertEquals(assignedAt, request.getHandledAt());
        assertEquals("QUOTED", request.getStatus());
    }

    @Test
    void workflowServiceCountsOnlyConfirmedPostQuoteSubmissions() {
        var requests = mock(PrintRequestRepository.class);
        var quotes = mock(PrintQuoteRepository.class);
        var histories = mock(PrintRequestStatusHistoryRepository.class);
        var review = mock(PrintRequestReviewPolicy.class);
        var workflow = new PrintRequestWorkflowService(requests, quotes, histories, review,
                new PrintRequestWorkflowPolicy());
        PrintRequest request = request(10, 7);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        Instant issued = Instant.parse("2026-10-25T10:00:00Z");
        var quote = PrintQuote.issueUpfront(10, 9, 1, 1, 1,
                new BigDecimal("100.00"), null, issued, issued.plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(quote, "printQuoteId", 101);
        request.markQuoted(101, 9, issued);
        when(requests.findByPrintRequestIdAndCustomerUserId(10, 7))
                .thenReturn(Optional.of(request));
        when(quotes.findByPrintRequestIdOrderByVersionNumberAsc(10))
                .thenReturn(List.of(quote));
        when(histories.findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(10))
                .thenReturn(List.of(
                        new PrintRequestStatusHistory(10, "QUOTED", "REQUIRES_REVISION", 7,
                                null, "POD_POST_QUOTE_REVISION_START:10:1"),
                        new PrintRequestStatusHistory(10, "REQUIRES_REVISION", "SUBMITTED", 7,
                                null, "POD_POST_QUOTE_REVISION:10:1")));

        var result = workflow.forCustomer(7, 10);

        assertEquals(2, result.remainingRevisions());
        assertEquals(issued.plus(Duration.ofHours(24)), result.quoteDeadline());
        verify(requests).findByPrintRequestIdAndCustomerUserId(10, 7);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
                () -> workflow.forCustomer(8, 10)).getStatus());
    }

    @Test
    void workflowReadModelUsesPersistedQuoteAndKeepsPaymentSeparateFromQuoteDeadline() {
        var policy = new PrintRequestWorkflowPolicy();
        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(3);
        Instant issued = Instant.parse("2026-10-25T10:00:00Z");
        var draft = policy.describe(request, List.of(), 0, false, issued);
        assertEquals("DRAFT", draft.displayStatus());
        assertNull(draft.quoteDeadline());
        assertNull(draft.remainingRevisions());
        assertTrue(draft.allowedActions().editFile());
        assertFalse(draft.allowedActions().pay());

        request.submit();
        var quote = PrintQuote.issueUpfront(10, 9, 1, 2, 3,
                new BigDecimal("160.00"), null, issued, issued.plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(quote, "printQuoteId", 101);
        request.markQuoted(101, 9, issued);
        var quoted = policy.describe(request, List.of(quote), 1, false,
                issued.plus(Duration.ofHours(23)));
        assertEquals("WAITING_QUOTE_ACCEPTANCE", quoted.displayStatus());
        assertEquals(issued.plus(Duration.ofHours(24)), quoted.quoteDeadline());
        assertEquals(2, quoted.remainingRevisions());
        assertTrue(quoted.allowedActions().acceptQuote());
        assertTrue(quoted.allowedActions().requestRevision());
        assertFalse(quoted.allowedActions().editFile());
        assertFalse(policy.describe(request, List.of(quote), 1, false,
                issued.plus(Duration.ofHours(24))).allowedActions().acceptQuote());

        quote.accept(issued.plus(Duration.ofHours(23)).plusSeconds(59));
        request.markQuoteAccepted(101, issued.plus(Duration.ofHours(23)).plusSeconds(59));
        var paying = policy.describe(request, List.of(quote), 1, false,
                issued.plus(Duration.ofHours(25)));
        assertEquals("PAYMENT_PENDING", paying.displayStatus());
        assertTrue(paying.allowedActions().pay());
        assertNull(paying.paymentExpiresAt());
        assertFalse(paying.allowedActions().cancel());
    }

    @Test
    void quoteDeadlineBoundaryAndExpiredDisplayUseServerTimeWithoutMutatingReads() {
        Instant deadline = Instant.parse("2026-10-26T10:00:00Z");
        assertTrue(PrintRequestWorkflowPolicy.beforeQuoteDeadline(
                deadline.minusSeconds(1), deadline));
        assertFalse(PrintRequestWorkflowPolicy.beforeQuoteDeadline(deadline, deadline));
        assertFalse(PrintRequestWorkflowPolicy.beforeQuoteDeadline(
                deadline.plusSeconds(1), deadline));

        PrintRequest request = request(10, 7);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        PrintQuote quote = PrintQuote.issueUpfront(10, 9, 1, 1, 1,
                new BigDecimal("100.00"), null,
                deadline.minus(Duration.ofHours(24)), deadline);
        ReflectionTestUtils.setField(quote, "printQuoteId", 101);
        request.markQuoted(101, 9, deadline.minus(Duration.ofHours(24)));
        var policy = new PrintRequestWorkflowPolicy();
        assertTrue(policy.describe(request, List.of(quote), 0, false,
                deadline.minusSeconds(1)).allowedActions().acceptQuote());
        var exactlyExpired = policy.describe(request, List.of(quote), 0, false, deadline);
        assertEquals("QUOTE_EXPIRED", exactlyExpired.displayStatus());
        assertFalse(exactlyExpired.allowedActions().acceptQuote());
        assertEquals("QUOTE_EXPIRED", policy.describe(request, List.of(quote), 0,
                false, deadline.plusSeconds(1)).displayStatus());
        var awaitingCorrection = request(11, 7);
        awaitingCorrection.useFileVersion(1);
        awaitingCorrection.useSpecVersion(1);
        awaitingCorrection.submit();
        awaitingCorrection.requireRevision();
        var expiredCorrection = policy.describe(awaitingCorrection,
                List.of(quote), 0, true, deadline.plusSeconds(1));
        assertEquals("QUOTE_EXPIRED", expiredCorrection.displayStatus());
        assertFalse(expiredCorrection.allowedActions().editFile());
        assertFalse(expiredCorrection.allowedActions().confirmPreview());
        assertFalse(expiredCorrection.allowedActions().submit());
        assertEquals("QUOTED", request.getStatus());

        quote.accept(deadline.minusSeconds(1));
        request.markQuoteAccepted(101, deadline.minusSeconds(1));
        var paying = policy.describe(request, List.of(quote), 0, false,
                deadline.plusSeconds(1));
        assertEquals("PAYMENT_PENDING", paying.displayStatus());
        assertTrue(paying.allowedActions().pay());
    }

    @Test
    void expirationWorkerContinuesAfterOneCandidateFailure() {
        var repository = mock(PrintRequestRepository.class);
        var quoteService = mock(PrintRequestQuoteService.class);
        var worker = new PrintQuoteExpirationWorker(repository, quoteService);
        when(repository.findUnacceptedQuoteExpirationCandidates(any(), eq(0), any()))
                .thenReturn(List.of(10, 11));
        when(quoteService.expireIfOverdue(10)).thenThrow(new IllegalStateException("bad record"));

        worker.expireOverdueQuotes();

        verify(quoteService).expireIfOverdue(11);
        verify(repository).findUnacceptedQuoteExpirationCandidates(any(), eq(0), any());
    }

    private PrintRequestRepository requests;
    private PrintRequestItemRepository items;
    private PrintRequestServiceImpl service;
    private PrintRequestStateService states;
    private PrintRequestFileVersionRepository fileVersions;
    private PrintRequestSpecVersionRepository specVersions;
    private PrintRequestStatusHistoryRepository histories;
    private PrintRequestSubmissionValidation submissionValidation;
    private PrintRequestReviewPolicy reviewPolicy;

    @BeforeEach
    void setUp() {
        requests = mock(PrintRequestRepository.class);
        items = mock(PrintRequestItemRepository.class);
        fileVersions = mock(PrintRequestFileVersionRepository.class);
        specVersions = mock(PrintRequestSpecVersionRepository.class);
        histories = mock(PrintRequestStatusHistoryRepository.class);
        submissionValidation = mock(PrintRequestSubmissionValidation.class);
        reviewPolicy = mock(PrintRequestReviewPolicy.class);
        service = new PrintRequestServiceImpl(requests, items,
                mock(PrintMaterialRepository.class), mock(ModelVersionRepository.class),
                mock(Model3DRepository.class), mock(ModelFileRepository.class),
                mock(AuditLogRepository.class), JsonMapper.builder().build(),
                histories);
        states = new PrintRequestStateService(requests, fileVersions, specVersions,
                histories, items, submissionValidation, reviewPolicy);
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
    void staffCorrectionPersistsVersionBoundJsonTransitionAndAudit() {
        var reviewRows = mock(PrintRequestTechnicalReviewRepository.class);
        var audits = mock(AuditLogRepository.class);
        var policy = new PrintRequestReviewPolicy(reviewRows, JsonMapper.builder().build());
        var staff = new PrintRequestTechnicalReviewService(requests, fileVersions,
                specVersions, reviewRows, histories, audits, policy, items,
                submissionValidation, JsonMapper.builder().build());
        PrintRequest request = request(10, 7);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(fileVersions.findByPrintRequestIdAndVersion(10, 1)).thenReturn(Optional.of(
                new PrintRequestFileVersion(10, 1, "shape.stl", 3L, "model/stl",
                        "/api/models/5/files/21/download", 7)));
        when(specVersions.findByPrintRequestIdAndVersion(10, 1)).thenReturn(Optional.of(
                new PrintRequestSpecVersion(10, 1, "{}", 7)));
        when(reviewRows.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(10))
                .thenReturn(List.of());
        when(reviewRows.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = staff.requestCorrection(9, 10,
                new RequestPrintCorrectionRequest(1, 1, "Unsupported geometry"));

        assertEquals("CORRECTION_REQUESTED", result.outcome());
        assertEquals("REQUIRES_REVISION", request.getStatus());
        var stored = ArgumentCaptor.forClass(PrintRequestTechnicalReview.class);
        verify(reviewRows).saveAndFlush(stored.capture());
        var data = JsonMapper.builder().build().readTree(stored.getValue().getReviewData());
        assertTrue(data.isObject());
        assertEquals(1, data.get("reviewedFileVersion").asInt());
        assertEquals(1, data.get("reviewedSpecVersion").asInt());
        assertEquals("Unsupported geometry", data.get("reason").asText());
        assertEquals(9, stored.getValue().getReviewerUserId());
        var transition = ArgumentCaptor.forClass(PrintRequestStatusHistory.class);
        verify(histories).saveAndFlush(transition.capture());
        assertEquals("SUBMITTED", transition.getValue().getFromStatus());
        assertEquals("REQUIRES_REVISION", transition.getValue().getToStatus());
        verify(audits).saveAndFlush(any(AuditLog.class));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> staff.requestCorrection(9, 10,
                        new RequestPrintCorrectionRequest(1, 1, "Again"))).getStatus());
        verify(reviewRows, times(1)).saveAndFlush(any());
    }

    @Test
    void staffApprovalTracksExactCurrentPairAndNeverWritesSameStatusHistory() {
        var reviewRows = mock(PrintRequestTechnicalReviewRepository.class);
        var audits = mock(AuditLogRepository.class);
        var policy = new PrintRequestReviewPolicy(reviewRows, JsonMapper.builder().build());
        var staff = new PrintRequestTechnicalReviewService(requests, fileVersions,
                specVersions, reviewRows, histories, audits, policy, items,
                submissionValidation, JsonMapper.builder().build());
        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(1);
        request.submit();
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(fileVersions.findByPrintRequestIdAndVersion(10, 2)).thenReturn(Optional.of(
                new PrintRequestFileVersion(10, 2, "shape.stl", 3L, "model/stl",
                        "/api/models/5/files/21/download", 7)));
        when(specVersions.findByPrintRequestIdAndVersion(10, 1)).thenReturn(Optional.of(
                new PrintRequestSpecVersion(10, 1, "{}", 7)));
        when(reviewRows.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(reviewRows.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(10))
                .thenReturn(List.of());

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> staff.approve(9, 10,
                        new ApprovePrintRequestReviewRequest(1, 1))).getStatus());
        verify(reviewRows, never()).saveAndFlush(any());
        doThrow(new ApiException(HttpStatus.CONFLICT, "Source unavailable"))
                .doNothing().when(submissionValidation).validate(eq(request), any(), anyList());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> staff.approve(9, 10,
                        new ApprovePrintRequestReviewRequest(2, 1))).getStatus());
        verify(reviewRows, never()).saveAndFlush(any());
        staff.approve(9, 10, new ApprovePrintRequestReviewRequest(2, 1));

        verify(submissionValidation, times(2)).validate(eq(request), any(), anyList());
        verifyNoInteractions(histories);
        var saved = ArgumentCaptor.forClass(PrintRequestTechnicalReview.class);
        verify(reviewRows).saveAndFlush(saved.capture());
        when(reviewRows.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(10))
                .thenReturn(List.of(saved.getValue()));
        assertEquals(PrintRequestReviewPolicy.Readiness.CURRENT_APPROVED, policy.readiness(request));
        request.useFileVersion(3);
        assertEquals(PrintRequestReviewPolicy.Readiness.FILE_CHANGED, policy.readiness(request));
        request.useSpecVersion(2);
        request.useFileVersion(2);
        assertEquals(PrintRequestReviewPolicy.Readiness.SPEC_CHANGED, policy.readiness(request));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> policy.requireCurrentApproved(request)).getStatus());
        when(reviewRows.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(10))
                .thenReturn(List.of(new PrintRequestTechnicalReview(10, 9,
                        "{\"outcome\":\"CORRECTION_REQUESTED\","
                        + "\"reviewedFileVersion\":2,\"reviewedSpecVersion\":2}")));
        assertEquals(PrintRequestReviewPolicy.Readiness.CORRECTION_PENDING,
                policy.readiness(request));
        ReflectionTestUtils.setField(request, "lockedAt", Instant.now());
        assertEquals(PrintRequestReviewPolicy.Readiness.LOCKED_OR_CANCELLED,
                policy.readiness(request));
    }

    @Test
    void staffDownloadRequiresTheCurrentRequestModelAndManagedStorageKey() throws Exception {
        var reviews = mock(PrintRequestTechnicalReviewRepository.class);
        var modelVersions = mock(ModelVersionRepository.class);
        var modelFiles = mock(ModelFileRepository.class);
        var storage = mock(ModelStorageService.class);
        var access = new PrintRequestStaffAccessService(requests, items, fileVersions,
                specVersions, reviews, modelVersions, modelFiles, storage,
                JsonMapper.builder().build());
        PrintRequest request = request(10, 7);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        when(requests.findById(10)).thenReturn(Optional.of(request));
        when(fileVersions.findByPrintRequestIdAndVersion(10, 1)).thenReturn(Optional.of(
                new PrintRequestFileVersion(10, 1, "shape.stl", 3L, "model/stl",
                        "/api/models/5/files/21/download", 7)));
        var modelFile = new ModelFile(12,
                ModelFileRole.SOURCE_MODEL, "stl", "models/5/versions/12/files/"
                + "12345678-1234-1234-1234-123456789abc.stl", "model/stl", null, 3L, true);
        ReflectionTestUtils.setField(modelFile, "fileId", 21);
        when(modelFiles.findById(21)).thenReturn(Optional.of(modelFile));
        var modelVersion = new ModelVersion(5, null, null, 7, 1, ModelSourceType.UPLOAD, null);
        ReflectionTestUtils.setField(modelVersion, "modelVersionId", 12);
        when(modelVersions.findByModelVersionIdAndModelId(12, 5))
                .thenReturn(Optional.of(modelVersion));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(item(30, 10, 3, 99, null)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> access.openCurrentFile(10)).getStatus());
        verifyNoInteractions(storage);

        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(item(30, 10, 3, 12, null)));
        when(storage.open(anyString())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        try (AuthorizedModelDownload download = access.openCurrentFile(10)) {
            assertArrayEquals(new byte[]{1, 2, 3}, download.inputStream().readAllBytes());
            assertEquals("print-request-10-file-v1.stl", download.filename());
        }
        when(specVersions.findByPrintRequestIdAndVersion(10, 1)).thenReturn(Optional.of(
                new PrintRequestSpecVersion(10, 1, "{\"items\":[]}", 7)));
        when(reviews.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(10))
                .thenReturn(List.of(new PrintRequestTechnicalReview(10, 9,
                        "{\"outcome\":\"APPROVED\",\"reviewedFileVersion\":1,"
                        + "\"reviewedSpecVersion\":1}")));
        var detail = access.detail(10);
        assertEquals("SUBMITTED", detail.status());
        assertEquals(1, detail.currentFile().version());
        assertEquals("APPROVED", detail.reviews().getFirst().outcome());
        assertFalse(detail.toString().contains("models/5/versions/12/files/"));
    }

    @Test
    void correctedRequestRequiresNewVersionPreviewAndResubmitsOnce() {
        PrintRequest request = request(10, 7);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.confirmCopyrightDeclaration();
        request.confirmPreview();
        request.submit();
        request.requireRevision();
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        when(reviewPolicy.requirePendingCorrection(request)).thenReturn(
                new PrintRequestReviewPolicy.Decision("CORRECTION_REQUESTED", 1, 1));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.confirmPreview(7, 10,
                        new ConfirmPrintRequestPreviewRequest(1, 1))).getStatus());
        request.useFileVersion(2);
        assertFalse(request.isPreviewConfirmed());
        when(fileVersions.findByPrintRequestIdAndVersion(10, 2)).thenReturn(Optional.of(
                new PrintRequestFileVersion(10, 2, "fixed.stl", 3L, "model/stl",
                        "/api/models/5/files/21/download", 7)));
        when(specVersions.findByPrintRequestIdAndVersion(10, 1)).thenReturn(Optional.of(
                new PrintRequestSpecVersion(10, 1, "{}", 7)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.submit(7, 10, new SubmitPrintRequestRequest(2, 1))).getStatus());
        states.confirmPreview(7, 10, new ConfirmPrintRequestPreviewRequest(2, 1));
        var result = states.submit(7, 10, new SubmitPrintRequestRequest(2, 1));
        assertEquals("SUBMITTED", result.status());
        var saved = ArgumentCaptor.forClass(PrintRequestStatusHistory.class);
        verify(histories).saveAndFlush(saved.capture());
        assertEquals("REQUIRES_REVISION", saved.getValue().getFromStatus());
        assertEquals("SUBMITTED", saved.getValue().getToStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> states.submit(7, 10, new SubmitPrintRequestRequest(2, 1))).getStatus());
        verify(histories, times(1)).saveAndFlush(any());
    }

    @Test
    void quotePolicyEnforcesFixed24HourWindowMaxThreeRevisionsAnd100PercentUpfront() {
        var quotes = mock(PrintQuoteRepository.class);
        var quoteItems = mock(PrintQuoteItemRepository.class);
        var reviewRows = mock(PrintRequestTechnicalReviewRepository.class);
        var audits = mock(AuditLogRepository.class);
        var mapper = JsonMapper.builder().build();
        var realReviewPolicy = new PrintRequestReviewPolicy(reviewRows, mapper);
        var realStates = new PrintRequestStateService(requests, fileVersions, specVersions,
                histories, items, submissionValidation, realReviewPolicy);
        var quoteService = new PrintRequestQuoteService(requests, items, fileVersions,
                specVersions, quotes, quoteItems, reviewRows, histories, audits,
                realReviewPolicy, mapper);

        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(3);
        request.confirmCopyrightDeclaration();
        request.confirmPreview();
        request.submit();

        var persistedQuotes = new ArrayList<PrintQuote>();
        var persistedReviews = new ArrayList<PrintRequestTechnicalReview>();
        var persistedHistories = new ArrayList<PrintRequestStatusHistory>();

        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(request));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(item(21, 10, 4, 12, null)));
        when(fileVersions.findByPrintRequestIdAndVersion(eq(10), anyInt()))
                .thenAnswer(call -> Optional.of(new PrintRequestFileVersion(10,
                        call.getArgument(1), "shape.stl", 4L, "model/stl",
                        "/api/models/5/files/21/download", 7)));
        when(specVersions.findByPrintRequestIdAndVersion(eq(10), anyInt()))
                .thenAnswer(call -> Optional.of(new PrintRequestSpecVersion(10,
                        call.getArgument(1), "{\"items\":[{\"printRequestItemId\":21,\"materialId\":4}]}", 7)));
        when(quotes.findByPrintRequestIdOrderByVersionNumberAsc(10))
                .thenAnswer(call -> List.copyOf(persistedQuotes));
        when(quotes.findByPrintQuoteIdAndPrintRequestId(anyInt(), eq(10)))
                .thenAnswer(call -> {
                    int id = call.getArgument(0);
                    return persistedQuotes.stream()
                            .filter(q -> Integer.valueOf(id).equals(q.getPrintQuoteId()))
                            .findFirst();
                });
        when(quotes.saveAndFlush(any())).thenAnswer(call -> {
            PrintQuote q = call.getArgument(0);
            if (q.getPrintQuoteId() == null) {
                ReflectionTestUtils.setField(q, "printQuoteId", 100 + persistedQuotes.size() + 1);
                persistedQuotes.add(q);
            }
            return q;
        });
        when(quoteItems.saveAndFlush(any())).thenAnswer(call -> {
            PrintQuoteItem qi = call.getArgument(0);
            ReflectionTestUtils.setField(qi, "printQuoteItemId", 500 + persistedQuotes.size());
            return qi;
        });
        when(quoteItems.findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(anyInt()))
                .thenReturn(List.of());
        when(reviewRows.saveAndFlush(any())).thenAnswer(call -> {
            PrintRequestTechnicalReview r = call.getArgument(0);
            persistedReviews.addFirst(r);
            return r;
        });
        when(reviewRows.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(10))
                .thenAnswer(call -> List.copyOf(persistedReviews));
        when(histories.saveAndFlush(any())).thenAnswer(call -> {
            PrintRequestStatusHistory h = call.getArgument(0);
            persistedHistories.add(h);
            return h;
        });
        when(histories.findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(10))
                .thenAnswer(call -> List.copyOf(persistedHistories));

        // Pre-quote edits (File V2, Spec V3) do not count against the 3 post-quote revisions.
        persistedReviews.addFirst(new PrintRequestTechnicalReview(10, 9,
                "{\"outcome\":\"APPROVED\",\"reviewedFileVersion\":2,\"reviewedSpecVersion\":3}"));

        var q1 = quoteService.issueQuote(9, 10, new CreatePrintQuoteRequest(2, 3, null,
                List.of(new CreatePrintQuoteItemRequest(21, new BigDecimal("120.00"),
                        new BigDecimal("30.00"), new BigDecimal("10.00")))));
        assertEquals(1, q1.versionNumber());
        assertEquals(new BigDecimal("160.00"), q1.totalAmount());
        assertEquals(new BigDecimal("160.00"), q1.depositAmount());
        assertEquals(new BigDecimal("0.00"), q1.remainingAmount());
        assertEquals(q1.issuedAt().plus(Duration.ofHours(24)), q1.expiresAt());
        assertEquals(0, q1.postQuoteRevisionsUsed());
        assertEquals(3, q1.remainingRevisions());
        Instant fixedDeadline = q1.expiresAt();

        // Execute 3 post-quote revisions (modifying both file and spec in Revision #1 still counts as 1)
        for (int rev = 1; rev <= 3; rev++) {
            int curFile = request.getCurrentFileVersion();
            int curSpec = request.getCurrentSpecVersion();
            Integer activeQuoteId = request.getCurrentQuoteId();

            var revResp = quoteService.requestRevision(7, 10, activeQuoteId,
                    new RequestPrintQuoteRevisionRequest(curFile, curSpec, "Revision " + rev));
            assertEquals("SUPERSEDED", revResp.quoteStatus());
            assertEquals(rev - 1, revResp.postQuoteRevisionsUsed());
            assertEquals(4 - rev, revResp.remainingRevisions());
            assertEquals("REQUIRES_REVISION", request.getStatus());
            assertNull(request.getCurrentQuoteId());

            // Modify both file and spec in Revision 1, or file in Revision 2/3
            request.useFileVersion(curFile + 1);
            if (rev == 1) {
                request.useSpecVersion(curSpec + 1);
            }
            int nextFile = request.getCurrentFileVersion();
            int nextSpec = request.getCurrentSpecVersion();

            realStates.confirmPreview(7, 10, new ConfirmPrintRequestPreviewRequest(nextFile, nextSpec));
            realStates.submit(7, 10, new SubmitPrintRequestRequest(nextFile, nextSpec));
            assertEquals("SUBMITTED", request.getStatus());
            assertEquals(rev, quoteService.countPostQuoteRevisions(10));

            // Staff must re-review and re-quote; new quote inherits exact fixedDeadline (T0 + 24h)
            persistedReviews.addFirst(new PrintRequestTechnicalReview(10, 9,
                    "{\"outcome\":\"APPROVED\",\"reviewedFileVersion\":" + nextFile
                            + ",\"reviewedSpecVersion\":" + nextSpec + "}"));
            var replacement = quoteService.issueQuote(9, 10, new CreatePrintQuoteRequest(
                    nextFile, nextSpec, null,
                    List.of(new CreatePrintQuoteItemRequest(21, new BigDecimal("140.00"),
                            new BigDecimal("40.00"), null))));
            assertEquals(rev + 1, replacement.versionNumber());
            assertEquals(fixedDeadline, replacement.expiresAt());
            assertEquals(new BigDecimal("180.00"), replacement.totalAmount());
            assertEquals(new BigDecimal("180.00"), replacement.depositAmount());
            assertEquals(new BigDecimal("0.00"), replacement.remainingAmount());
        }

        // 4th revision must be rejected (max 3 post-quote revisions reached)
        Integer quote4Id = request.getCurrentQuoteId();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> quoteService.requestRevision(7, 10, quote4Id,
                        new RequestPrintQuoteRevisionRequest(
                                request.getCurrentFileVersion(),
                                request.getCurrentSpecVersion(),
                                "4th revision attempt"))).getStatus());
        assertEquals("QUOTED", request.getStatus());
        assertEquals(quote4Id, request.getCurrentQuoteId());

        // Older superseded quote (Q1) cannot be accepted
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> quoteService.acceptQuote(7, 10, q1.printQuoteId())).getStatus());

        // Accepting current quote (Q4) before deadline succeeds
        var accepted = quoteService.acceptQuote(7, 10, quote4Id);
        assertEquals("ACCEPTED", accepted.quoteStatus());
        assertNotNull(accepted.acceptedAt());
        assertEquals("QUOTE_ACCEPTED", request.getStatus());
    }

    @Test
    void quoteExpirationCancelsUnacceptedRequestAndLeavesAcceptedQuoteIntact() {
        var quotes = mock(PrintQuoteRepository.class);
        var quoteItems = mock(PrintQuoteItemRepository.class);
        var reviewRows = mock(PrintRequestTechnicalReviewRepository.class);
        var audits = mock(AuditLogRepository.class);
        var mapper = JsonMapper.builder().build();
        var quoteService = new PrintRequestQuoteService(requests, items, fileVersions,
                specVersions, quotes, quoteItems, reviewRows, histories, audits,
                new PrintRequestReviewPolicy(reviewRows, mapper), mapper);

        PrintRequest unaccepted = request(10, 7);
        unaccepted.useFileVersion(1);
        unaccepted.useSpecVersion(1);
        unaccepted.submit();
        Instant pastIssued = Instant.now().minus(Duration.ofHours(25));
        Instant pastExpires = pastIssued.plus(Duration.ofHours(24));
        PrintQuote overdueQuote = PrintQuote.issueUpfront(10, 9, 1, 1, 1,
                new BigDecimal("100.00"), null, pastIssued, pastExpires);
        ReflectionTestUtils.setField(overdueQuote, "printQuoteId", 77);
        unaccepted.markQuoted(77, 9, pastIssued);

        when(requests.findOwnedByIdForUpdate(10, 7)).thenReturn(Optional.of(unaccepted));
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(unaccepted));
        when(quotes.findByPrintRequestIdOrderByVersionNumberAsc(10))
                .thenReturn(List.of(overdueQuote));
        when(quotes.findByPrintQuoteIdAndPrintRequestId(77, 10))
                .thenReturn(Optional.of(overdueQuote));

        // Attempting to accept after the 24-hour deadline expires the quote and cancels the request
        assertThrows(QuoteExpiredException.class,
                () -> quoteService.acceptQuote(7, 10, 77));
        assertEquals("EXPIRED", overdueQuote.getQuoteStatus());
        assertEquals("CANCELLED", unaccepted.getStatus());
        assertEquals(PrintRequestQuoteService.QUOTE_EXPIRED_REASON,
                unaccepted.getCancellationReason());
        assertNotNull(unaccepted.getCancelledAt());
        assertNull(unaccepted.getCurrentQuoteId());
        var historyCaptor = ArgumentCaptor.forClass(PrintRequestStatusHistory.class);
        verify(histories).saveAndFlush(historyCaptor.capture());
        assertEquals("QUOTED", historyCaptor.getValue().getFromStatus());
        assertEquals("CANCELLED", historyCaptor.getValue().getToStatus());
        assertEquals("POD_QUOTE_EXPIRED:10", historyCaptor.getValue().getSourceEventId());
        assertFalse(quoteService.expireIfOverdue(10));
        verify(histories, times(1)).saveAndFlush(any());

        // An already ACCEPTED quote with payment pending must NOT be cancelled by unaccepted-quote expiry
        PrintRequest acceptedRequest = request(11, 7);
        acceptedRequest.useFileVersion(1);
        acceptedRequest.useSpecVersion(1);
        acceptedRequest.submit();
        PrintQuote acceptedQuote = PrintQuote.issueUpfront(11, 9, 1, 1, 1,
                new BigDecimal("100.00"), null, pastIssued, pastExpires);
        ReflectionTestUtils.setField(acceptedQuote, "printQuoteId", 88);
        acceptedQuote.accept(pastIssued.plus(Duration.ofHours(1)));
        acceptedRequest.markQuoted(88, 9, pastIssued);
        acceptedRequest.markQuoteAccepted(88, pastIssued.plus(Duration.ofHours(1)));

        when(requests.findByIdForUpdate(11)).thenReturn(Optional.of(acceptedRequest));
        when(quotes.findByPrintRequestIdOrderByVersionNumberAsc(11))
                .thenReturn(List.of(acceptedQuote));
        assertFalse(quoteService.expireIfOverdue(11));
        assertEquals("QUOTE_ACCEPTED", acceptedRequest.getStatus());
        assertNull(acceptedRequest.getCancelledAt());
    }

    @Test
    void inconsistentFirstQuoteDeadlineCannotDriveExpirationOrBuyerAcceptance() {
        var quotes = mock(PrintQuoteRepository.class);
        var quoteItems = mock(PrintQuoteItemRepository.class);
        var reviewRows = mock(PrintRequestTechnicalReviewRepository.class);
        var audits = mock(AuditLogRepository.class);
        var mapper = JsonMapper.builder().build();
        var service = new PrintRequestQuoteService(requests, items, fileVersions,
                specVersions, quotes, quoteItems, reviewRows, histories, audits,
                new PrintRequestReviewPolicy(reviewRows, mapper), mapper);
        PrintRequest request = request(10, 7);
        request.useFileVersion(1);
        request.useSpecVersion(1);
        request.submit();
        Instant issued = Instant.now().minus(Duration.ofHours(1));
        var quote = PrintQuote.issueUpfront(10, 9, 1, 1, 1,
                new BigDecimal("100.00"), null, issued, issued.plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(quote, "printQuoteId", 101);
        ReflectionTestUtils.setField(quote, "expiresAt", issued.plus(Duration.ofHours(48)));
        request.markQuoted(101, 9, issued);
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(quotes.findByPrintRequestIdOrderByVersionNumberAsc(10)).thenReturn(List.of(quote));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> service.expireIfOverdue(10)).getStatus());
        var workflow = new PrintRequestWorkflowPolicy().describe(request, List.of(quote),
                0, false, Instant.now());
        assertNull(workflow.quoteDeadline());
        assertFalse(workflow.allowedActions().acceptQuote());
        assertEquals("QUOTED", request.getStatus());
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(histories, audits);
    }

    @Test
    void productionQueueActivationRequiresVerified100PercentPaymentAndIsIdempotent() {
        var quotes = mock(PrintQuoteRepository.class);
        var audits = mock(AuditLogRepository.class);
        var production = new PrintRequestProductionService(
                requests, items, quotes, histories, audits, JsonMapper.builder().build());

        PrintRequest request = request(10, 7);
        request.useFileVersion(2);
        request.useSpecVersion(1);
        request.submit();
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        PrintQuote quote = PrintQuote.issueUpfront(10, 9, 1, 2, 1,
                new BigDecimal("250.00"), null, issuedAt, issuedAt.plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(quote, "printQuoteId", 90);
        request.markQuoted(90, 9, issuedAt);
        PrintRequestItem reqItem = item(31, 10, 4, 12, null);

        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(quotes.findByPrintQuoteIdAndPrintRequestId(90, 10)).thenReturn(Optional.of(quote));
        when(items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(10))
                .thenReturn(List.of(reqItem));

        // Cannot enqueue before quote is ACCEPTED
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.activateAfterVerifiedPayment(
                        10, 90, 501, "POD", "SUCCESS", new BigDecimal("250.00"))).getStatus());

        quote.accept(Instant.now().minus(Duration.ofHours(1)));
        request.markQuoteAccepted(90, Instant.now().minus(Duration.ofHours(1)));

        // Rejects partial payment (< 100% of total) or non-SUCCESS status
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.activateAfterVerifiedPayment(
                        10, 91, 501, "POD", "SUCCESS", new BigDecimal("250.00"))).getStatus());
        request.useFileVersion(3);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.activateAfterVerifiedPayment(
                        10, 90, 501, "POD", "SUCCESS", new BigDecimal("250.00"))).getStatus());
        request.useFileVersion(2);
        request.useSpecVersion(2);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.activateAfterVerifiedPayment(
                        10, 90, 501, "POD", "SUCCESS", new BigDecimal("250.00"))).getStatus());
        request.useSpecVersion(1);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.activateAfterVerifiedPayment(
                        10, 90, 501, "POD", "SUCCESS", new BigDecimal("125.00"))).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> production.activateAfterVerifiedPayment(
                        10, 90, 501, "POD", "PENDING", new BigDecimal("250.00"))).getStatus());
        assertNull(request.getLockedAt());

        // Verified 100% payment activates production queue and locks request + items
        var activated = production.activateAfterVerifiedPayment(
                10, 90, 501, "POD", "SUCCESS", new BigDecimal("250.00"));
        assertEquals("IN_QUEUE", activated.status());
        assertNotNull(request.getLockedAt());
        assertEquals("QUEUED", reqItem.getProductionStatus());

        // Second callback for same payment is idempotent (no duplicate history or item saves)
        var replay = production.activateAfterVerifiedPayment(
                10, 90, 501, "POD", "SUCCESS", new BigDecimal("250.00"));
        assertEquals("IN_QUEUE", replay.status());
        verify(histories, times(1)).saveAndFlush(any());
        verify(items, times(1)).saveAndFlush(reqItem);
        var queuedWorkflow = new PrintRequestWorkflowPolicy().describe(request, List.of(quote),
                0, false, Instant.now());
        assertEquals("QUEUED", queuedWorkflow.displayStatus());
        assertFalse(queuedWorkflow.allowedActions().editFile());
        assertFalse(queuedWorkflow.allowedActions().editSpecification());
        assertFalse(queuedWorkflow.allowedActions().cancel());
        assertFalse(queuedWorkflow.allowedActions().pay());
    }

    @Test
    void persistedVerifiedPodPaymentDelegatesToExistingQueueTransition() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        var payment = verifiedPodPayment(501, 10, 90, 7);
        var attempt = verifiedPodAttempt(501);
        var request = acceptedRequest(10, 7, 90);
        when(transactionRepo.findById(501)).thenReturn(Optional.of(payment));
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        when(attemptRepo.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(501, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));

        handler.handleVerifiedSuccess(501);

        verify(requests).findByIdForUpdate(10);
        verify(production).activateAfterVerifiedPayment(10, 90, 501,
                "POD", "SUCCESS", new BigDecimal("250.00"));
        verify(transactionRepo, never()).saveAndFlush(any());
        verify(attemptRepo, never()).saveAndFlush(any());
    }

    @Test
    void fulfillmentRejectsWrongOwnerOrRequestAndUnverifiedFinancialState() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        var payment = verifiedPodPayment(501, 10, 90, 8);
        when(transactionRepo.findById(501)).thenReturn(Optional.of(payment));
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(acceptedRequest(10, 7, 90)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(501)).getStatus());
        var pending = verifiedPodPayment(502, 10, 90, 7);
        ReflectionTestUtils.setField(pending, "status", "PENDING");
        when(transactionRepo.findById(502)).thenReturn(Optional.of(pending));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(502)).getStatus());
        ReflectionTestUtils.setField(pending, "status", "FAILED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(502)).getStatus());
        var wrongRequest = verifiedPodPayment(503, 10, 90, 7);
        when(transactionRepo.findById(503)).thenReturn(Optional.of(wrongRequest));
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(acceptedRequest(11, 7, 90)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(503)).getStatus());
        verifyNoInteractions(attemptRepo, production);
    }

    @Test
    void fulfillmentRejectsFailedAttemptEvenWithTransactionMarkedSuccess() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        when(transactionRepo.findById(501))
                .thenReturn(Optional.of(verifiedPodPayment(501, 10, 90, 7)));
        when(requests.findByIdForUpdate(10))
                .thenReturn(Optional.of(acceptedRequest(10, 7, 90)));
        var failedAttempt = verifiedPodAttempt(501);
        ReflectionTestUtils.setField(failedAttempt, "attemptStatus", "FAILED");
        when(attemptRepo.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(501, "ZALOPAY"))
                .thenReturn(Optional.of(failedAttempt));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(501)).getStatus());
        verifyNoInteractions(production);
    }

    @Test
    void fulfillmentRejectsAttemptWithoutPersistedProviderVerification() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        when(transactionRepo.findById(501))
                .thenReturn(Optional.of(verifiedPodPayment(501, 10, 90, 7)));
        when(requests.findByIdForUpdate(10))
                .thenReturn(Optional.of(acceptedRequest(10, 7, 90)));
        var unverified = verifiedPodAttempt(501);
        ReflectionTestUtils.setField(unverified, "providerEventSnapshot", null);
        when(attemptRepo.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(501, "ZALOPAY"))
                .thenReturn(Optional.of(unverified));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(501)).getStatus());
        unverified.recordEvent("{\"amount\":249,\"zp_trans_id\":\"provider-transaction\"}");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(501)).getStatus());
        verifyNoInteractions(production);
    }

    @Test
    void verifiedProviderAmountMayBeANumericJsonString() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        when(transactionRepo.findById(501))
                .thenReturn(Optional.of(verifiedPodPayment(501, 10, 90, 7)));
        when(requests.findByIdForUpdate(10))
                .thenReturn(Optional.of(acceptedRequest(10, 7, 90)));
        var attempt = verifiedPodAttempt(501);
        attempt.recordEvent("{\"amount\":\"250.00\",\"zp_trans_id\":\"provider-transaction\"}");
        when(attemptRepo.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(501, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));

        handler.handleVerifiedSuccess(501);

        verify(production).activateAfterVerifiedPayment(10, 90, 501,
                "POD", "SUCCESS", new BigDecimal("250.00"));
    }

    @Test
    void fulfillmentRejectsLateSuccessAndOtherPaymentReplay() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        when(transactionRepo.findById(501))
                .thenReturn(Optional.of(verifiedPodPayment(501, 10, 90, 7)));
        when(attemptRepo.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(501, "ZALOPAY"))
                .thenReturn(Optional.of(verifiedPodAttempt(501)));
        var request = acceptedRequest(10, 7, 90);
        request.cancelExpiredQuote("Payment expired", Instant.now());
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(request));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(501)).getStatus());

        var queued = acceptedRequest(10, 7, 90);
        queued.lockForProduction(Instant.now());
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(queued));
        when(histories.findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(10))
                .thenReturn(List.of(new PrintRequestStatusHistory(10, "QUOTE_ACCEPTED",
                        "IN_QUEUE", 7, null, "POD_PAYMENT_VERIFIED:10:tx:999")));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                () -> handler.handleVerifiedSuccess(501)).getStatus());
        verifyNoInteractions(production);
    }

    @Test
    void sameVerifiedPaymentReplayIsRecognizedByHistory() {
        var transactionRepo = mock(PaymentTransactionRepository.class);
        var attemptRepo = mock(PaymentAttemptRepository.class);
        var production = mock(PrintRequestProductionService.class);
        var handler = new PrintRequestPaymentFulfillmentService(transactionRepo, attemptRepo,
                requests, histories, production, JsonMapper.builder().build());
        when(transactionRepo.findById(501))
                .thenReturn(Optional.of(verifiedPodPayment(501, 10, 90, 7)));
        when(attemptRepo.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(501, "ZALOPAY"))
                .thenReturn(Optional.of(verifiedPodAttempt(501)));
        var queued = acceptedRequest(10, 7, 90);
        queued.lockForProduction(Instant.now());
        when(requests.findByIdForUpdate(10)).thenReturn(Optional.of(queued));
        when(histories.findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(10))
                .thenReturn(List.of(new PrintRequestStatusHistory(10, "QUOTE_ACCEPTED",
                        "IN_QUEUE", 7, null, "POD_PAYMENT_VERIFIED:10:tx:501")));

        handler.handleVerifiedSuccess(501);

        verify(production).activateAfterVerifiedPayment(10, 90, 501,
                "POD", "SUCCESS", new BigDecimal("250.00"));
    }

    @Test
    void fulfillmentWorkerContinuesAfterFailureAndRetriesOnNextScan() {
        var jdbc = mock(JdbcTemplate.class);
        var handler = mock(PrintRequestPaymentFulfillmentService.class);
        var worker = new PrintRequestPaymentFulfillmentWorker(jdbc, handler);
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(0), eq(100)))
                .thenReturn(List.of(501, 502));
        when(handler.handleVerifiedSuccess(501))
                .thenThrow(new IllegalStateException("temporary database failure"))
                .thenReturn(mock(com.project.w_3d_world.features.printing.dto.response.PrintRequestStateResponse.class));

        worker.reconcileVerifiedPayments();
        worker.reconcileVerifiedPayments();

        verify(handler, times(2)).handleVerifiedSuccess(501);
        verify(handler, times(2)).handleVerifiedSuccess(502);
    }

    @Test
    void fulfillmentWorkerScansBoundedPagesWithoutDroppingLaterPayments() {
        var jdbc = mock(JdbcTemplate.class);
        var handler = mock(PrintRequestPaymentFulfillmentService.class);
        var worker = new PrintRequestPaymentFulfillmentWorker(jdbc, handler);
        List<Integer> firstBatch = java.util.stream.IntStream.rangeClosed(1, 100)
                .boxed().toList();
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(0), eq(100)))
                .thenReturn(firstBatch);
        when(jdbc.queryForList(anyString(), eq(Integer.class), eq(100), eq(100)))
                .thenReturn(List.of(101));

        worker.reconcileVerifiedPayments();
        worker.reconcileVerifiedPayments();

        verify(handler).handleVerifiedSuccess(1);
        verify(handler).handleVerifiedSuccess(100);
        verify(handler).handleVerifiedSuccess(101);
        verify(handler, times(101)).handleVerifiedSuccess(anyInt());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(Integer.class), eq(0), eq(100));
        assertTrue(sql.getValue().contains("t.status = 'SUCCESS'"));
        assertTrue(sql.getValue().contains("r.status = 'QUOTE_ACCEPTED'"));
        assertTrue(sql.getValue().contains("a.verified_at IS NOT NULL"));
    }

    private static PaymentTransaction verifiedPodPayment(int id, int requestId, int quoteId, int payerId) {
        var payment = new PaymentTransaction("PRINT_QUOTE", "operation", payerId,
                "FULL", new BigDecimal("250.00"));
        payment.setSource("PRINT_REQUEST", requestId);
        payment.setSource("PRINT_QUOTE", quoteId);
        payment.markSuccess();
        payment.fulfillment("RECONCILIATION_REQUIRED");
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    private static PaymentAttempt verifiedPodAttempt(int transactionId) {
        var attempt = new PaymentAttempt(transactionId, "attempt", "ZALOPAY");
        attempt.reference("provider-reference");
        attempt.providerTransaction("provider-transaction");
        attempt.recordEvent("{\"amount\":250,\"zp_trans_id\":\"provider-transaction\","
                + "\"app_trans_id\":\"provider-reference\"}");
        attempt.success();
        return attempt;
    }

    private static PrintRequest acceptedRequest(int requestId, int customerId, int quoteId) {
        var request = request(requestId, customerId);
        request.useFileVersion(2);
        request.useSpecVersion(1);
        request.submit();
        request.markQuoted(quoteId, 9, Instant.now());
        request.markQuoteAccepted(quoteId, Instant.now());
        return request;
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
