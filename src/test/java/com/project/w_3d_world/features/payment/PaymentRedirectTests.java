package com.project.w_3d_world.features.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.order.service.OrderPaymentService;
import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.entity.*;
import com.project.w_3d_world.features.payment.repository.*;
import com.project.w_3d_world.features.payment.service.*;
import com.project.w_3d_world.features.payment.service.impl.PaymentServiceImpl;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentRedirectTests {
    PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    ZaloPayClient provider = mock(ZaloPayClient.class);
    PaymentSourceResolver sources = mock(PaymentSourceResolver.class);
    UserRepository users = mock(UserRepository.class);
    OrderPaymentService fulfillment = mock(OrderPaymentService.class);
    PaymentServiceImpl service;
    PaymentTransaction transaction;
    PaymentAttempt attempt;

    @BeforeEach
    void setup() {
        var config = new ZaloPayProperties(2554, "key1", "key2", "create", "query", "refund", "",
                "http://localhost:5173/payment-result");
        service = new PaymentServiceImpl(transactions, attempts, provider, config, sources, users,
                fulfillment);
        transaction = new PaymentTransaction("PHYSICAL_ORDER", "operation", 7, "FULL", new BigDecimal("10000"));
        ReflectionTestUtils.setField(transaction, "id", 11);
        attempt = new PaymentAttempt(11, "request", "ZALOPAY");
        ReflectionTestUtils.setField(attempt, "id", 22);
        attempt.initializeProvider(1, 2554, System.currentTimeMillis(), java.time.Instant.now().plusSeconds(900));
        attempt.reference("251025_22");
        when(transactions.findById(11)).thenReturn(Optional.of(transaction));
        when(transactions.lockById(11)).thenReturn(Optional.of(transaction));
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(11, "ZALOPAY"))
                .thenReturn(Optional.of(attempt));
        when(attempts.lockById(22)).thenReturn(Optional.of(attempt));
    }

    @Test
    void browserReturnUsesConfiguredFeUrlAndOmitsCallback() {
        when(users.lockById(7)).thenReturn(Optional.of(new User("buyer@example.com", "hash", "Buyer", null)));
        when(sources.resolve(7, "PHYSICAL_ORDER", 3, "FULL", null)).thenReturn(new BigDecimal("10000"));
        when(transactions.findByOperationKey(anyString())).thenReturn(Optional.empty());
        when(transactions.saveAndFlush(any())).thenReturn(transaction);
        when(attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(11, "ZALOPAY"))
                .thenReturn(Optional.empty());
        when(attempts.saveAndFlush(any())).thenAnswer(call -> {
            PaymentAttempt saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 22);
            return saved;
        });
        when(provider.createOrder(anyMap())).thenReturn(Map.of("return_code", 1, "order_url", "https://sandbox.example/pay"));
        service.create(7, new CreatePaymentRequest("PHYSICAL_ORDER", 3, "FULL", null, "Order", null));
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(provider).createOrder(payload.capture());
        assertFalse(payload.getValue().containsKey("callback_url"));
        assertTrue(payload.getValue().get("embed_data").toString()
                .contains("http://localhost:5173/payment-result?transactionId=11"));
    }

    @Test
    void queryVerifiesAmountAndAppliesPaymentOnlyOnce() {
        when(provider.queryOrder("251025_22")).thenReturn(Map.of("return_code", 1, "amount", 10000, "zp_trans_id", "123"));
        assertEquals("SUCCESS", service.status(7, 11).status());
        assertEquals("SUCCESS", service.status(7, 11).status());
        verify(fulfillment, times(1)).verified(transaction);
        verify(provider, times(1)).queryOrder(anyString());
    }

    @Test
    void wrongOwnerAndMismatchedAmountCannotConfirmPayment() {
        assertThrows(ApiException.class, () -> service.status(9, 11));
        verifyNoInteractions(provider);
        when(provider.queryOrder(anyString())).thenReturn(Map.of("return_code", 1, "amount", 20000, "zp_trans_id", "123"));
        assertEquals("UNKNOWN", service.status(7, 11).status());
        verifyNoInteractions(fulfillment);
    }

    @Test
    void processingStaysPendingAndProviderFailureBecomesFailed() {
        when(provider.queryOrder(anyString())).thenReturn(Map.of("return_code", 3));
        assertEquals("PENDING", service.status(7, 11).status());
        when(provider.queryOrder(anyString())).thenReturn(Map.of("return_code", 2));
        assertEquals("FAILED", service.status(7, 11).status());
        verifyNoInteractions(fulfillment);
    }
}
