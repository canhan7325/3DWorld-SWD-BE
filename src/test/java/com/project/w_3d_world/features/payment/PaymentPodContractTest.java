package com.project.w_3d_world.features.payment;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.order.service.OrderPaymentService;
import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentAttemptRepository;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.payment.service.PaymentSourceResolver;
import com.project.w_3d_world.features.payment.service.ZaloPayClient;
import com.project.w_3d_world.features.payment.service.ZaloPayProperties;
import com.project.w_3d_world.features.payment.service.impl.PaymentServiceImpl;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentPodContractTest {
    private PaymentTransactionRepository transactions;
    private PaymentAttemptRepository attempts;
    private ZaloPayClient provider;
    private PaymentSourceResolver sources;
    private UserRepository users;
    private OrderPaymentService orders;
    private PaymentServiceImpl payments;

    @BeforeEach
    void setUp() {
        transactions = mock(PaymentTransactionRepository.class);
        attempts = mock(PaymentAttemptRepository.class);
        provider = mock(ZaloPayClient.class);
        sources = mock(PaymentSourceResolver.class);
        users = mock(UserRepository.class);
        orders = mock(OrderPaymentService.class);
        payments = new PaymentServiceImpl(transactions, attempts, provider,
                new ZaloPayProperties(1, "test-key", "test-key", "", "", "", ""),
                sources, users, orders);
    }

    @Test
    void ownerReadsPersistedPodPaymentAndLatestAttemptWithoutProviderEffects() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        attempt.initializeProvider(2, 1, 1L, Instant.parse("2026-10-26T12:00:00Z"));
        ReflectionTestUtils.setField(attempt, "id", 22);
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7))
                .thenReturn(List.of(transaction));
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));

        var status = payments.getPodStatus(7, 10);

        assertEquals(1, status.transactionId());
        assertEquals(10, status.printRequestId());
        assertEquals(55, status.quoteId());
        assertEquals("PENDING", status.financialStatus());
        assertEquals("PENDING", status.fulfillmentStatus());
        assertEquals(new BigDecimal("100.00"), status.amount());
        assertEquals("VND", status.currency());
        assertEquals(22, status.latestAttemptId());
        assertEquals("REQUESTED", status.latestAttemptStatus());
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), status.attemptExpiresAt());
        assertNotNull(status.createdAt());
        assertFalse(status.attemptReconciliationPending());
        verifyNoInteractions(provider, sources, orders);
        verify(transactions, never()).saveAndFlush(any());
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void unknownProviderCreateRemainsFinanciallyPendingAndVisibleAsReconciliation() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        attempt.reconciliation("Provider create outcome is unknown; query before retrying.");
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7))
                .thenReturn(List.of(transaction));
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));

        var status = payments.getPodStatus(7, 10);

        assertEquals("PENDING", status.financialStatus());
        assertEquals("REQUESTED", status.latestAttemptStatus());
        assertTrue(status.attemptReconciliationPending());
        verifyNoInteractions(provider, sources, orders);
    }

    @Test
    void financialSuccessDoesNotClaimProductionFulfillment() {
        PaymentTransaction transaction = podTransaction();
        transaction.markSuccess();
        transaction.fulfillment("RECONCILIATION_REQUIRED");
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        attempt.success();
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7))
                .thenReturn(List.of(transaction));
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));

        var status = payments.getPodStatus(7, 10);

        assertEquals("SUCCESS", status.financialStatus());
        assertEquals("SUCCESS", status.latestAttemptStatus());
        assertEquals("RECONCILIATION_REQUIRED", status.fulfillmentStatus());
        assertNotNull(status.completedAt());
        verifyNoInteractions(provider, orders);
    }

    @Test
    void definitiveFailureAndAbsentAttemptAreReportedWithoutInventedExpiry() {
        PaymentTransaction transaction = podTransaction();
        transaction.markFailed();
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7))
                .thenReturn(List.of(transaction));

        var status = payments.getPodStatus(7, 10);

        assertEquals("FAILED", status.financialStatus());
        assertNull(status.latestAttemptStatus());
        assertNull(status.attemptExpiresAt());
        verifyNoInteractions(provider, orders);
    }

    @Test
    void otherPayerAndWrongSourceAreNonDisclosingNotFound() {
        PaymentTransaction wrongSource = new PaymentTransaction("PHYSICAL_ORDER", "key", 7,
                "FULL", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(wrongSource, "id", 2);
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 8))
                .thenReturn(List.of());
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7))
                .thenReturn(List.of(wrongSource));

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> payments.getPodStatus(8, 10)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> payments.getPodStatus(7, 10)).getStatus());
        verifyNoInteractions(attempts, provider, orders);
    }

    @Test
    void invalidIdAndMultipleObligationsNeverChooseAnAmbiguousPayment() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> payments.getPodStatus(7, 0)).getStatus());
        PaymentTransaction first = podTransaction();
        PaymentTransaction second = podTransaction();
        ReflectionTestUtils.setField(second, "id", 2);
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7))
                .thenReturn(List.of(first, second));

        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ApiException.class, () -> payments.getPodStatus(7, 10)).getStatus());
        verifyNoInteractions(attempts, provider, orders);
    }

    @Test
    void podResolverRequiresCurrentAcceptedFullQuoteUnderRequestLock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var resolver = new PaymentSourceResolver(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(55), eq(7)))
                .thenReturn(List.of(new PaymentSourceResolver.PodSource(
                        10, 55, new BigDecimal("100.00"))));

        var resolved = resolver.resolvePod(7, "PRINT_QUOTE", 55, "FULL");

        assertEquals(10, resolved.printRequestId());
        assertEquals(55, resolved.printQuoteId());
        assertEquals(new BigDecimal("100.00"), resolved.amount());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq(55), eq(7));
        assertTrue(sql.getValue().contains("r.current_quote_id = q.print_quote_id"));
        assertTrue(sql.getValue().contains("r.status = 'QUOTE_ACCEPTED'"));
        assertTrue(sql.getValue().contains("q.file_version_snapshot = r.current_file_version"));
        assertTrue(sql.getValue().contains("q.remaining_amount = 0 FOR UPDATE OF r"));

        assertThrows(ApiException.class,
                () -> resolver.resolvePod(7, "PRINT_REQUEST", 10, "REMAINING"));
        assertThrows(ApiException.class,
                () -> resolver.resolvePod(7, "PRINT_QUOTE", 55, "DEPOSIT"));
        assertThrows(ApiException.class,
                () -> resolver.resolvePod(7, "PRINT_QUOTE", 0, "FULL"));
    }

    @Test
    void requestAndQuoteAliasesReuseTheSamePaymentObligation() {
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(users.lockById(7)).thenReturn(Optional.of(user));
        when(sources.resolvePod(eq(7), anyString(), anyInt(), eq("FULL")))
                .thenReturn(new PaymentSourceResolver.PodSource(
                        10, 55, new BigDecimal("100.00")));
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7)).thenReturn(List.of());
        PaymentTransaction existing = podTransaction();
        when(transactions.findByOperationKey(anyString())).thenReturn(Optional.of(existing));
        PaymentAttempt attempt = new PaymentAttempt(1, "retry", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));

        var fromRequest = payments.create(7, request("PRINT_REQUEST", 10));
        var fromQuote = payments.create(7, request("PRINT_QUOTE", 55));

        assertEquals(fromRequest.transactionId(), fromQuote.transactionId());
        var keys = ArgumentCaptor.forClass(String.class);
        verify(transactions, times(2)).findByOperationKey(keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        verify(transactions, never()).saveAndFlush(any());
        verifyNoInteractions(provider);
    }

    @Test
    void anExistingLegacyPodObligationBlocksASecondCharge() {
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(users.lockById(7)).thenReturn(Optional.of(user));
        when(sources.resolvePod(7, "PRINT_QUOTE", 55, "FULL"))
                .thenReturn(new PaymentSourceResolver.PodSource(
                        10, 55, new BigDecimal("100.00")));
        PaymentTransaction legacy = podTransaction();
        ReflectionTestUtils.setField(legacy, "operationKey", "legacy-request-key");
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7)).thenReturn(List.of(legacy));

        assertThrows(ApiException.class,
                () -> payments.create(7, request("PRINT_QUOTE", 55)));

        verify(transactions, never()).saveAndFlush(any());
        verifyNoInteractions(provider);
    }

    @Test
    void verifiedPodCallbackStaysFinanciallySuccessfulButDoesNotQueue() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        attempt.reference("provider-ref");
        when(provider.verifyCallback(anyString(), eq("valid-mac"))).thenReturn(true);
        when(attempts.findByExternalReferenceAndProviderName("provider-ref", "ZALOPAY"))
                .thenReturn(Optional.of(attempt));
        when(transactions.lockById(1)).thenReturn(Optional.of(transaction));
        String callback = "{\"app_trans_id\":\"provider-ref\",\"amount\":100,\"app_id\":1}";

        payments.callback(callback, "valid-mac");
        payments.callback(callback, "valid-mac");

        assertEquals("SUCCESS", transaction.getStatus());
        assertEquals("RECONCILIATION_REQUIRED", transaction.getFulfillmentStatus());
        assertEquals("SUCCESS", attempt.getAttemptStatus());
        verifyNoInteractions(orders);
        verify(transactions, times(2)).lockById(1);
    }

    @Test
    void wrongProviderAmountCannotMarkPodPaymentSuccessful() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        attempt.reference("provider-ref");
        when(provider.verifyCallback(anyString(), eq("valid-mac"))).thenReturn(true);
        when(attempts.findByExternalReferenceAndProviderName("provider-ref", "ZALOPAY"))
                .thenReturn(Optional.of(attempt));
        when(transactions.lockById(1)).thenReturn(Optional.of(transaction));

        assertThrows(ApiException.class, () -> payments.callback(
                "{\"app_trans_id\":\"provider-ref\",\"amount\":99,\"app_id\":1}",
                "valid-mac"));

        assertEquals("PENDING", transaction.getStatus());
        verifyNoInteractions(orders);
    }

    @Test
    void unrecognizedCreateResultStaysUnknownUntilProviderQuery() {
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(users.lockById(7)).thenReturn(Optional.of(user));
        when(sources.resolvePod(7, "PRINT_QUOTE", 55, "FULL"))
                .thenReturn(new PaymentSourceResolver.PodSource(
                        10, 55, new BigDecimal("100.00")));
        when(transactions.findByPrintRequestIdAndPayerUserId(10, 7)).thenReturn(List.of());
        PaymentTransaction transaction = podTransaction();
        when(transactions.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(transactions.saveAndFlush(any())).thenReturn(transaction);
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.empty());
        when(attempts.saveAndFlush(any())).thenAnswer(call -> {
            PaymentAttempt value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 22);
            return value;
        });
        when(provider.signCreateOrder(anyString())).thenReturn("signed");
        when(provider.createOrder(anyMap())).thenReturn(Map.of("return_code", 0));

        var response = payments.create(7, request("PRINT_QUOTE", 55));

        assertEquals("UNKNOWN", response.status());
        assertEquals("PENDING", transaction.getStatus());
        var saved = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attempts).saveAndFlush(saved.capture());
        assertEquals("REQUESTED", saved.getValue().getAttemptStatus());
        assertNotNull(saved.getValue().getReconciliationReason());
        verifyNoInteractions(orders);
    }

    @Test
    void providerQuerySuccessKeepsPodInReconciliationWithoutQueueing() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        attempt.reference("provider-ref");
        when(attempts.findByAttemptStatusAndProviderName("REQUESTED", "ZALOPAY"))
                .thenReturn(List.of(attempt));
        when(attempts.lockById(22)).thenReturn(Optional.of(attempt));
        when(transactions.lockById(1)).thenReturn(Optional.of(transaction));
        when(provider.queryOrder("provider-ref")).thenReturn(Map.of(
                "return_code", 1, "amount", 100, "zp_trans_id", "verified-provider-id"));

        payments.reconcilePending();

        assertEquals("SUCCESS", transaction.getStatus());
        assertEquals("SUCCESS", attempt.getAttemptStatus());
        assertEquals("RECONCILIATION_REQUIRED", transaction.getFulfillmentStatus());
        assertEquals("verified-provider-id", attempt.getProviderTransactionId());
        verifyNoInteractions(orders);
    }

    @Test
    void inconclusiveProviderQueryDoesNotCancelOrFailPod() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        attempt.reference("provider-ref");
        when(attempts.findByAttemptStatusAndProviderName("REQUESTED", "ZALOPAY"))
                .thenReturn(List.of(attempt));
        when(attempts.lockById(22)).thenReturn(Optional.of(attempt));
        when(transactions.lockById(1)).thenReturn(Optional.of(transaction));
        when(provider.queryOrder("provider-ref")).thenReturn(Map.of("return_code", 2));

        payments.reconcilePending();

        assertEquals("PENDING", transaction.getStatus());
        assertEquals("REQUESTED", attempt.getAttemptStatus());
        verifyNoInteractions(orders);
    }

    @Test
    void definitiveProviderFailureDoesNotQueuePodWithoutBe2OutcomeHandler() {
        PaymentTransaction transaction = podTransaction();
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        attempt.reference("provider-ref");
        when(attempts.findByAttemptStatusAndProviderName("REQUESTED", "ZALOPAY"))
                .thenReturn(List.of(attempt));
        when(attempts.lockById(22)).thenReturn(Optional.of(attempt));
        when(transactions.lockById(1)).thenReturn(Optional.of(transaction));
        when(provider.queryOrder("provider-ref")).thenReturn(Map.of("return_code", -1));

        payments.reconcilePending();

        assertEquals("FAILED", transaction.getStatus());
        assertEquals("FAILED", attempt.getAttemptStatus());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHYSICAL_ORDER", "RENTAL_CONTRACT", "WALLET"})
    void existingNonPodSourcesKeepTheirResolverPath(String sourceType) {
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(users.lockById(7)).thenReturn(Optional.of(user));
        when(sources.resolve(eq(7), eq(sourceType), eq(10), anyString(), nullable(BigDecimal.class)))
                .thenReturn(new BigDecimal("100.00"));
        PaymentTransaction existing = new PaymentTransaction(sourceType, "existing", 7,
                "WALLET".equals(sourceType) ? "TOPUP" : "FULL",
                new BigDecimal("100.00"));
        existing.setSource(sourceType, 10);
        ReflectionTestUtils.setField(existing, "id", 1);
        when(transactions.findByOperationKey(anyString())).thenReturn(Optional.of(existing));
        PaymentAttempt attempt = new PaymentAttempt(1, "attempt", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));
        var input = new CreatePaymentRequest(sourceType, 10,
                "WALLET".equals(sourceType) ? "TOPUP" : "FULL",
                "WALLET".equals(sourceType) ? new BigDecimal("100.00") : null,
                null, "WALLET".equals(sourceType) ? "retry" : null);

        assertEquals(1, payments.create(7, input).transactionId());

        verify(sources).resolve(eq(7), eq(sourceType), eq(10), anyString(), nullable(BigDecimal.class));
        verify(sources, never()).resolvePod(anyInt(), anyString(), anyInt(), anyString());
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHYSICAL_ORDER", "RENTAL_CONTRACT", "WALLET"})
    void nonPodProviderRejectionKeepsExistingFailedBehavior(String sourceType) {
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(users.lockById(7)).thenReturn(Optional.of(user));
        when(sources.resolve(eq(7), eq(sourceType), eq(10), anyString(), nullable(BigDecimal.class)))
                .thenReturn(new BigDecimal("100.00"));
        PaymentTransaction transaction = new PaymentTransaction(sourceType, "existing", 7,
                "WALLET".equals(sourceType) ? "TOPUP" : "FULL",
                new BigDecimal("100.00"));
        transaction.setSource(sourceType, 10);
        ReflectionTestUtils.setField(transaction, "id", 1);
        when(transactions.findByOperationKey(anyString())).thenReturn(Optional.of(transaction));
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(1, "ZALOPAY"))
                .thenReturn(Optional.empty());
        when(attempts.saveAndFlush(any())).thenAnswer(call -> {
            PaymentAttempt value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 22);
            return value;
        });
        when(provider.signCreateOrder(anyString())).thenReturn("signed");
        when(provider.createOrder(anyMap())).thenReturn(Map.of("return_code", 0));
        var input = new CreatePaymentRequest(sourceType, 10,
                "WALLET".equals(sourceType) ? "TOPUP" : "FULL",
                "WALLET".equals(sourceType) ? new BigDecimal("100.00") : null,
                null, "WALLET".equals(sourceType) ? "retry" : null);

        payments.create(7, input);

        assertEquals("FAILED", transaction.getStatus());
        var saved = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attempts).saveAndFlush(saved.capture());
        assertEquals("FAILED", saved.getValue().getAttemptStatus());
    }

    private static CreatePaymentRequest request(String type, int id) {
        return new CreatePaymentRequest(type, id, "FULL", null, null, null);
    }

    private static PaymentTransaction podTransaction() {
        PaymentTransaction value = new PaymentTransaction("PRINT_QUOTE", "operation-key", 7,
                "FULL", new BigDecimal("100.00"));
        value.setSource("PRINT_REQUEST", 10);
        value.setSource("PRINT_QUOTE", 55);
        ReflectionTestUtils.setField(value, "id", 1);
        return value;
    }
}
