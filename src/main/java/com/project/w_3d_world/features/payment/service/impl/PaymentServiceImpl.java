package com.project.w_3d_world.features.payment.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PaymentResponse;
import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentAttemptRepository;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.payment.service.PaymentService;
import com.project.w_3d_world.features.payment.service.ZaloPayClient;
import com.project.w_3d_world.features.payment.service.ZaloPayProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentTransactionRepository transactions;
    private final PaymentAttemptRepository attempts;
    private final ZaloPayClient zaloPay;
    private final ZaloPayProperties properties;

    @Override
    @Transactional
    public PaymentResponse create(int userId, CreatePaymentRequest request) {
        validateSource(request.sourceType());
        String operationKey = "ZALOPAY:" + userId + ":" + request.sourceType()
                + ":" + request.sourceId() + ":" + request.stage();
        PaymentTransaction transaction = transactions.findByOperationKey(operationKey)
                .orElseGet(() -> createTransaction(userId, request, operationKey));
        PaymentAttempt attempt = new PaymentAttempt(
                transaction.getId(), operationKey + ":ATTEMPT:" + UUID.randomUUID(), "ZALOPAY");
        attempts.saveAndFlush(attempt);

        String appTransId = LocalDate.now(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
                + "_" + transaction.getId();
        long appTime = System.currentTimeMillis();
        String embedData = "{\"redirecturl\":\"" + properties.callbackUrl() + "\"}";
        String item = "[]";
        String signData = properties.appId() + "|" + appTransId + "|user-" + userId + "|"
                + toAmount(request.amount()) + "|" + appTime + "|" + embedData + "|" + item;
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", properties.appId());
        payload.put("app_user", "user-" + userId);
        payload.put("app_trans_id", appTransId);
        payload.put("app_time", appTime);
        payload.put("amount", toAmount(request.amount()));
        payload.put("description", request.description() == null ? "3DWorld payment" : request.description());
        payload.put("embed_data", embedData);
        payload.put("item", item);
        payload.put("callback_url", properties.callbackUrl());
        payload.put("mac", zaloPay.signCreateOrder(signData));

        Map<String, Object> response = zaloPay.createOrder(payload);
        String returnCode = String.valueOf(response.getOrDefault("return_code", "0"));
        if (!"1".equals(returnCode)) {
            attempt.fail();
            transaction.markFailed();
        } else {
            attempt.reference(appTransId);
        }
        return new PaymentResponse(
                transaction.getId(),
                attempt.getId(),
                appTransId,
                request.amount(),
                transaction.getStatus(),
                stringValue(response.get("order_url")),
                stringValue(response.get("zp_trans_token")));
    }

    @Override
    @Transactional
    public void callback(String data, String mac) {
        if (!zaloPay.verifyCallback(data, mac)) {
            throw ApiException.invalidRequest("Invalid ZaloPay callback signature.");
        }
        Map<String, Object> callback = parseCallback(data);
        String appTransId = stringValue(callback.get("app_trans_id"));
        PaymentAttempt attempt = attempts.findByExternalReferenceAndProviderName(appTransId, "ZALOPAY")
                .orElseThrow(() -> ApiException.invalidRequest("Unknown ZaloPay transaction."));
        if ("SUCCESS".equals(attempt.getAttemptStatus())) return;
        attempt.success();
        transactions.findById(attempt.getTransactionId()).ifPresent(PaymentTransaction::markSuccess);
    }

    private PaymentTransaction createTransaction(int userId, CreatePaymentRequest request, String key) {
        PaymentTransaction transaction = new PaymentTransaction(
                request.sourceType(), key, userId, request.stage(), request.amount());
        transaction.setSource(request.sourceType(), request.sourceId());
        return transactions.saveAndFlush(transaction);
    }

    private void validateSource(String sourceType) {
        if (!Map.of("PHYSICAL_ORDER", true, "RENTAL_CONTRACT", true, "PRINT_REQUEST", true,
                "SUBSCRIPTION", true, "WALLET", true, "PRINT_QUOTE", true).containsKey(sourceType)) {
            throw ApiException.invalidRequest("Unsupported payment source.");
        }
    }

    private int toAmount(BigDecimal amount) {
        try {
            return amount.intValueExact();
        } catch (ArithmeticException exception) {
            throw ApiException.invalidRequest("Amount must be a whole VND value.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCallback(String data) {
        try { return new tools.jackson.databind.ObjectMapper().readValue(data, Map.class); }
        catch (Exception exception) { throw ApiException.invalidRequest("Invalid callback payload."); }
    }

    private String stringValue(Object value) { return value == null ? null : String.valueOf(value); }
}
