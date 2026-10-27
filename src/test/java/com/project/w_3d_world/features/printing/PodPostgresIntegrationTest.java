package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.service.impl.ModelDownloadServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelUploadServiceImpl;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.printing.dto.request.ApprovePrintRequestReviewRequest;
import com.project.w_3d_world.features.printing.dto.request.ConfirmPrintRequestPreviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.SubmitPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationItemRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileVersionService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestPaymentFulfillmentService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestPaymentFulfillmentWorker;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestProductionManagementService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestProductionService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestQuoteService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestServiceImpl;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingAddressService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingReadinessService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestSpecificationService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffQueueService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestTechnicalReviewService;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestSubmissionValidation;
import com.project.w_3d_world.features.shipping.entity.Shipment;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "pod.pg.integration", matches = "true")
@SpringBootTest(classes = PodPostgresIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PodPostgresIntegrationTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55439/postgres";

    @Autowired JdbcTemplate jdbc;
    @Autowired PrintRequestFileCreateService fileCreate;
    @Autowired PrintRequestService printRequestService;
    @Autowired ModelUploadService modelUploadService;
    @Autowired PrintRequestFileVersionService fileVersionService;
    @Autowired PrintRequestRepository requests;
    @Autowired PrintRequestSpecificationService specifications;
    @Autowired PrintRequestStateService states;
    @Autowired PrintRequestStaffQueueService queue;
    @Autowired PrintRequestTechnicalReviewService reviews;
    @Autowired PrintRequestQuoteService quotes;
    @Autowired PrintRequestPaymentFulfillmentService fulfillment;
    @Autowired PrintRequestPaymentFulfillmentWorker worker;
    @Autowired PrintRequestProductionManagementService production;
    @Autowired PrintRequestShippingReadinessService shipping;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry properties) {
        if (!URL.equals(System.getProperty("pod.pg.jdbcUrl"))) {
            throw new IllegalStateException("POD integration requires the exact disposable localhost URL.");
        }
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> "pod_mvp_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "false");
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.main.web-application-type", () -> "none");
    }

    @BeforeAll
    void requireDisposableSchema() throws Exception {
        Path target = Path.of(System.getProperty("user.dir"), "target").toRealPath();
        Path data = Path.of(System.getProperty("pod.pg.dataDir")).toRealPath();
        assertTrue(data.startsWith(target));
        assertEquals(data, Path.of(jdbc.queryForObject(
                "select current_setting('data_directory')", String.class)).toRealPath());
        assertEquals(55439, jdbc.queryForObject("select inet_server_port()", Integer.class));
        assertEquals(48, jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema='public' and table_type='BASE TABLE'", Integer.class));
    }

    @AfterAll
    void verifySchemaRemains48Tables() {
        assertEquals(48, jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema='public' and table_type='BASE TABLE'", Integer.class),
                "Database schema must remain exactly 48 tables after all integration tests.");
    }

    @Test
    void customerToProductionAndShippingReadinessUsesRealTransactions() {
        int customer = user("customer");
        int staff = user("staff");
        int material = material();
        int address = address(customer);
        var created = fileCreate.create(customer, material, true,
                new MockMultipartFile("file", "test.stl", "model/stl", new byte[]{1, 2, 3, 4}));
        int requestId = created.printRequestId();
        PrintRequest draft = requests.findById(requestId).orElseThrow();
        assertEquals("DRAFT", draft.getStatus());
        assertEquals(1, draft.getCurrentFileVersion());
        assertEquals(1, draft.getCurrentSpecVersion());
        int itemId = jdbc.queryForObject("select print_request_item_id from print_request_items "
                + "where print_request_id=?", Integer.class, requestId);
        jdbc.update("update print_requests set shipping_address_id=?, "
                + "shipping_address_snapshot=?::jsonb where print_request_id=?", address,
                "{\"recipientName\":\"Buyer\",\"phone\":\"0900000000\","
                        + "\"addressLine\":\"1 Test St\",\"province\":\"HCMC\","
                        + "\"district\":\"District 1\",\"ward\":\"Ward 1\"}", requestId);
        var change = new UpdatePrintRequestSpecificationItemRequest(itemId, material,
                "Black", "STANDARD", 20, new BigDecimal("10"),
                new BigDecimal("10"), new BigDecimal("10"), 2, "Test");
        var spec = specifications.update(customer, requestId,
                new UpdatePrintRequestSpecificationRequest(1, List.of(change)));
        assertEquals(2, spec.version());
        var stale = assertThrows(ApiException.class,
                () -> specifications.update(customer, requestId,
                        new UpdatePrintRequestSpecificationRequest(1, List.of(change))));
        assertEquals(409, stale.getStatus().value());
        states.confirmPreview(customer, requestId, new ConfirmPrintRequestPreviewRequest(1, 2));
        states.submit(customer, requestId, new SubmitPrintRequestRequest(1, 2));
        assertEquals("SUBMITTED", requests.findById(requestId).orElseThrow().getStatus());
        assertEquals(1, queue.list(staff, "UNASSIGNED", 0, 20).content().size());
        queue.claim(staff, requestId);
        assertEquals(staff, requests.findById(requestId).orElseThrow().getHandledByUserId());
        reviews.approve(staff, requestId, new ApprovePrintRequestReviewRequest(1, 2));
        var quote = quotes.issueQuote(staff, requestId,
                new CreatePrintQuoteRequest(1, 2, null, List.of(
                        new CreatePrintQuoteItemRequest(itemId, new BigDecimal("100"),
                                new BigDecimal("50"), BigDecimal.ZERO))));
        assertEquals(new BigDecimal("150.00"), quote.totalAmount());
        quotes.acceptQuote(customer, requestId, quote.printQuoteId());

        int transactionId = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "isolated-pod-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                customer, quote.totalAmount());
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", transactionId,
                "isolated-attempt-" + UUID.randomUUID(), "app-" + transactionId,
                "provider-" + transactionId, "{\"amount\":\"150.00\",\"zp_trans_id\":\"provider-"
                        + transactionId + "\"}");
        fulfillment.handleVerifiedSuccess(transactionId);
        fulfillment.handleVerifiedSuccess(transactionId);
        assertEquals("IN_QUEUE", requests.findById(requestId).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from print_request_status_history "
                + "where print_request_id=? and to_status='IN_QUEUE'", Integer.class, requestId));
        assertFalse(shipping.forCustomer(customer, requestId).shippingReady());
        production.start(staff, requestId, itemId);
        production.start(staff, requestId, itemId);
        production.complete(staff, requestId, itemId);
        production.complete(staff, requestId, itemId);
        assertEquals("COMPLETED", jdbc.queryForObject("select production_status from print_request_items "
                + "where print_request_item_id=?", String.class, itemId));
        assertTrue(shipping.forCustomer(customer, requestId).shippingReady());
    }

    @Test
    void paymentFulfillmentRejectionAndWorkerIdempotency() {
        int customer = user("pay-customer");
        int foreignPayer = user("foreign-payer");
        int staff = user("pay-staff");
        int material = material();
        int address = address(customer);
        var created = fileCreate.create(customer, material, true,
                new MockMultipartFile("file", "pay.stl", "model/stl", new byte[]{1, 2, 3}));
        int requestId = created.printRequestId();
        int itemId = jdbc.queryForObject("select print_request_item_id from print_request_items "
                + "where print_request_id=?", Integer.class, requestId);
        jdbc.update("update print_requests set shipping_address_id=?, shipping_address_snapshot=?::jsonb "
                + "where print_request_id=?", address,
                "{\"recipientName\":\"PayBuyer\",\"phone\":\"0911111111\","
                        + "\"addressLine\":\"2 Test St\",\"province\":\"HCMC\","
                        + "\"district\":\"District 1\",\"ward\":\"Ward 1\"}", requestId);
        states.confirmPreview(customer, requestId, new ConfirmPrintRequestPreviewRequest(1, 1));
        states.submit(customer, requestId, new SubmitPrintRequestRequest(1, 1));
        queue.claim(staff, requestId);
        reviews.approve(staff, requestId, new ApprovePrintRequestReviewRequest(1, 1));
        var quote = quotes.issueQuote(staff, requestId,
                new CreatePrintQuoteRequest(1, 1, null, List.of(
                        new CreatePrintQuoteItemRequest(itemId, new BigDecimal("200"),
                                new BigDecimal("50"), BigDecimal.ZERO))));
        quotes.acceptQuote(customer, requestId, quote.printQuoteId());

        // Test 1: Rejection on wrong payer
        int wrongPayerTx = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "tx-wrong-payer-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                foreignPayer, quote.totalAmount());
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", wrongPayerTx,
                "att-" + UUID.randomUUID(), "app-" + wrongPayerTx, "prov-" + wrongPayerTx,
                "{\"amount\":\"250.00\",\"zp_trans_id\":\"prov-" + wrongPayerTx + "\"}");
        var errWrongPayer = assertThrows(ApiException.class,
                () -> fulfillment.handleVerifiedSuccess(wrongPayerTx));
        assertEquals(409, errWrongPayer.getStatus().value());

        // Test 2: Rejection on partial/wrong amount
        int wrongAmountTx = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "tx-wrong-amount-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                customer, new BigDecimal("100.00"));
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", wrongAmountTx,
                "att-" + UUID.randomUUID(), "app-" + wrongAmountTx, "prov-" + wrongAmountTx,
                "{\"amount\":\"100.00\",\"zp_trans_id\":\"prov-" + wrongAmountTx + "\"}");
        var errWrongAmount = assertThrows(ApiException.class,
                () -> fulfillment.handleVerifiedSuccess(wrongAmountTx));
        assertEquals(409, errWrongAmount.getStatus().value());

        // Test 3: Rejection on corrupted/mismatched provider snapshot
        int corruptTx = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "tx-corrupt-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                customer, quote.totalAmount());
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", corruptTx,
                "att-" + UUID.randomUUID(), "app-" + corruptTx, "prov-" + corruptTx,
                "{\"amount\":\"999.00\",\"zp_trans_id\":\"prov-" + corruptTx + "\"}");
        var errCorrupt = assertThrows(ApiException.class,
                () -> fulfillment.handleVerifiedSuccess(corruptTx));
        assertEquals(409, errCorrupt.getStatus().value());

        // Test 4: Valid transaction & attempt executed via worker reconcileVerifiedPayments
        int validTx = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "tx-valid-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                customer, quote.totalAmount());
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", validTx,
                "att-" + UUID.randomUUID(), "app-" + validTx, "prov-" + validTx,
                "{\"amount\":\"250.00\",\"zp_trans_id\":\"prov-" + validTx + "\"}");

        worker.reconcileVerifiedPayments();
        assertEquals("IN_QUEUE", requests.findById(requestId).orElseThrow().getStatus());
        assertNotNull(requests.findById(requestId).orElseThrow().getLockedAt());

        // Duplicate worker invocation must be completely safe and create no duplicate transitions
        worker.reconcileVerifiedPayments();
        assertEquals(1, jdbc.queryForObject("select count(*) from print_request_status_history "
                + "where print_request_id=? and to_status='IN_QUEUE'", Integer.class, requestId));
    }

    @Test
    void staffProductionMultiItemAndSequentialTransitions() {
        int customer = user("multi-customer");
        int staff = user("multi-staff");
        int material = material();
        int address = address(customer);

        var upload1 = modelUploadService.uploadModel(customer, new CreateModelRequest("part1", null),
                new MockMultipartFile("file", "part1.stl", "model/stl", new byte[]{1, 2}));

        var itemReq1 = new CreatePrintRequestItemRequest(upload1.modelVersionId(), material,
                null, null, null, null, null, null, 1, "Item 1");
        var itemReq2 = new CreatePrintRequestItemRequest(upload1.modelVersionId(), material,
                null, null, null, null, null, null, 2, "Item 2");

        var created = printRequestService.createDraft(customer,
                new CreatePrintRequestRequest("Multi part print", true, List.of(itemReq1, itemReq2)));
        int requestId = created.printRequestId();
        fileVersionService.recordInitial(customer, requestId, upload1, "part1.stl");
        specifications.recordInitial(customer, requestId);

        List<Integer> itemIds = jdbc.queryForList("select print_request_item_id from print_request_items "
                + "where print_request_id=? order by print_request_item_id asc", Integer.class, requestId);
        assertEquals(2, itemIds.size());
        int item1 = itemIds.get(0);
        int item2 = itemIds.get(1);

        jdbc.update("update print_requests set shipping_address_id=?, shipping_address_snapshot=?::jsonb "
                + "where print_request_id=?", address,
                "{\"recipientName\":\"MultiBuyer\",\"phone\":\"0922222222\","
                        + "\"addressLine\":\"3 Test St\",\"province\":\"HCMC\","
                        + "\"district\":\"District 1\",\"ward\":\"Ward 1\"}", requestId);

        states.confirmPreview(customer, requestId, new ConfirmPrintRequestPreviewRequest(1, 1));
        states.submit(customer, requestId, new SubmitPrintRequestRequest(1, 1));
        queue.claim(staff, requestId);
        reviews.approve(staff, requestId, new ApprovePrintRequestReviewRequest(1, 1));

        var quote = quotes.issueQuote(staff, requestId,
                new CreatePrintQuoteRequest(1, 1, null, List.of(
                        new CreatePrintQuoteItemRequest(item1, new BigDecimal("100"), new BigDecimal("20"), BigDecimal.ZERO),
                        new CreatePrintQuoteItemRequest(item2, new BigDecimal("150"), new BigDecimal("30"), BigDecimal.ZERO))));
        assertEquals(new BigDecimal("300.00"), quote.totalAmount());
        quotes.acceptQuote(customer, requestId, quote.printQuoteId());

        int transactionId = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "tx-multi-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                customer, quote.totalAmount());
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", transactionId,
                "att-" + UUID.randomUUID(), "app-" + transactionId, "prov-" + transactionId,
                "{\"amount\":\"300.00\",\"zp_trans_id\":\"prov-" + transactionId + "\"}");
        fulfillment.handleVerifiedSuccess(transactionId);

        // Verification: cannot skip from QUEUED directly to COMPLETED
        var invalidTransition = assertThrows(ApiException.class,
                () -> production.complete(staff, requestId, item1));
        assertEquals(409, invalidTransition.getStatus().value());

        // Item 1: QUEUED -> PRINTING -> COMPLETED
        production.start(staff, requestId, item1);
        production.start(staff, requestId, item1); // duplicate start is idempotent
        production.complete(staff, requestId, item1);
        production.complete(staff, requestId, item1); // duplicate complete is idempotent

        // Shipping readiness must be FALSE because Item 2 is still QUEUED
        var readinessPartial = shipping.forCustomer(customer, requestId);
        assertFalse(readinessPartial.shippingReady());
        assertTrue(readinessPartial.shippingBlockers().contains("PRODUCTION_ITEMS_INCOMPLETE"));
        assertEquals(1, readinessPartial.completedItems());
        assertEquals(2, readinessPartial.totalItems());

        // Item 2: QUEUED -> PRINTING -> COMPLETED
        production.start(staff, requestId, item2);
        production.complete(staff, requestId, item2);

        // Both items are COMPLETED -> Shipping readiness must now be TRUE
        var readinessFull = shipping.forCustomer(customer, requestId);
        assertTrue(readinessFull.shippingReady());
        assertTrue(readinessFull.shippingBlockers().isEmpty());
        assertEquals(2, readinessFull.completedItems());
        assertEquals(2, readinessFull.totalItems());
    }

    @Test
    void shippingReadinessEnforcesAddressAndExistingShipmentConflict() {
        int customer = user("ship-customer");
        int staff = user("ship-staff");
        int material = material();
        var created = fileCreate.create(customer, material, true,
                new MockMultipartFile("file", "ship.stl", "model/stl", new byte[]{1, 2}));
        int requestId = created.printRequestId();
        int itemId = jdbc.queryForObject("select print_request_item_id from print_request_items "
                + "where print_request_id=?", Integer.class, requestId);

        states.confirmPreview(customer, requestId, new ConfirmPrintRequestPreviewRequest(1, 1));
        states.submit(customer, requestId, new SubmitPrintRequestRequest(1, 1));
        queue.claim(staff, requestId);
        reviews.approve(staff, requestId, new ApprovePrintRequestReviewRequest(1, 1));

        var quote = quotes.issueQuote(staff, requestId,
                new CreatePrintQuoteRequest(1, 1, null, List.of(
                        new CreatePrintQuoteItemRequest(itemId, new BigDecimal("80"), new BigDecimal("20"), BigDecimal.ZERO))));
        quotes.acceptQuote(customer, requestId, quote.printQuoteId());

        int transactionId = jdbc.queryForObject("insert into transactions "
                + "(transaction_type,purpose,operation_key,print_request_id,print_quote_id,"
                + "payer_user_id,payment_stage,settlement_mode,amount,status,completed_at,fulfillment_status) "
                + "values ('PAYMENT','POD',?,?,?,?, 'FULL','PLATFORM',?,'SUCCESS',now(),"
                + "'RECONCILIATION_REQUIRED') returning transaction_id", Integer.class,
                "tx-ship-" + UUID.randomUUID(), requestId, quote.printQuoteId(),
                customer, quote.totalAmount());
        jdbc.update("insert into transaction_attempts "
                + "(transaction_id,attempt_number,idempotency_key,provider_name,external_reference,"
                + "provider_transaction_id,provider_event_snapshot,verified_at,attempt_status) "
                + "values (?,1,?,'ZALOPAY',?,?,?::jsonb,now(),'SUCCESS')", transactionId,
                "att-" + UUID.randomUUID(), "app-" + transactionId, "prov-" + transactionId,
                "{\"amount\":\"100.00\",\"zp_trans_id\":\"prov-" + transactionId + "\"}");
        fulfillment.handleVerifiedSuccess(transactionId);

        production.start(staff, requestId, itemId);
        production.complete(staff, requestId, itemId);

        // Blocker 1: Address missing
        var noAddress = shipping.forCustomer(customer, requestId);
        assertFalse(noAddress.shippingReady());
        assertTrue(noAddress.shippingBlockers().contains("SHIPPING_ADDRESS_MISSING"));

        // Attach valid address
        int address = address(customer);
        jdbc.update("update print_requests set shipping_address_id=?, shipping_address_snapshot=?::jsonb "
                + "where print_request_id=?", address,
                "{\"recipientName\":\"ShipBuyer\",\"phone\":\"0933333333\","
                        + "\"addressLine\":\"4 Test St\",\"province\":\"HCMC\","
                        + "\"district\":\"District 1\",\"ward\":\"Ward 1\"}", requestId);

        // Now shipping ready is true
        assertTrue(shipping.forCustomer(customer, requestId).shippingReady());

        // Blocker 2: Conflicting existing shipment in 48-table schema
        int shipmentId = jdbc.queryForObject("insert into shipments "
                + "(print_request_id, carrier_name, tracking_number, status, created_at) "
                + "values (?, 'GHN', 'TRACK-TEST-12345', 'PENDING', now()) returning shipment_id",
                Integer.class, requestId);
        try {
            var existingShipmentReadiness = shipping.forCustomer(customer, requestId);
            assertFalse(existingShipmentReadiness.shippingReady());
            assertTrue(existingShipmentReadiness.shipmentExists());
            assertTrue(existingShipmentReadiness.shippingBlockers().contains("POD_SHIPMENT_ALREADY_EXISTS"));
        } finally {
            jdbc.update("delete from shipments where shipment_id=?", shipmentId);
        }
    }

    private int user(String label) {
        String key = label + "-" + UUID.randomUUID() + "@example.invalid";
        return jdbc.queryForObject("insert into users(email,password_hash,full_name) "
                + "values (?,'isolated','POD integration') returning user_id", Integer.class, key);
    }

    private int material() {
        return jdbc.queryForObject("insert into print_materials "
                + "(material_name,technology_code,unit,estimated_unit_cost,is_active) "
                + "values ('PLA','FDM','GRAM',100,true) returning material_id", Integer.class);
    }

    private int address(int customer) {
        return jdbc.queryForObject("insert into user_addresses "
                + "(user_id,recipient_name,phone,address_line,province,district,ward) "
                + "values (?,?,?,?,?,?,?) returning address_id", Integer.class,
                customer, "Buyer", "0900000000", "1 Test St", "HCMC", "District 1", "Ward 1");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Model3D.class, PrintRequest.class,
            PaymentTransaction.class, AuditLog.class, Shipment.class})
    @EnableJpaRepositories(basePackageClasses = {Model3DRepository.class, PrintRequestRepository.class,
            PaymentTransactionRepository.class, AuditLogRepository.class})
    @Import({ModelUploadServiceImpl.class, ModelDownloadServiceImpl.class,
            PrintRequestServiceImpl.class, PrintRequestFileCreateService.class,
            PrintRequestFileVersionService.class, PrintRequestSpecificationService.class,
            PrintRequestStateService.class,
            PrintRequestStaffQueueService.class, PrintRequestTechnicalReviewService.class,
            PrintRequestQuoteService.class, PrintRequestPaymentFulfillmentService.class,
            PrintRequestPaymentFulfillmentWorker.class,
            PrintRequestProductionService.class, PrintRequestProductionManagementService.class,
            PrintRequestShippingReadinessService.class, PrintRequestReviewPolicy.class,
            PrintRequestSubmissionValidation.class})
    static class Config {
        @Bean ModelStorageService storage() {
            return new LocalModelStorageService(Path.of(System.getProperty("pod.pg.storageRoot")));
        }
    }
}
