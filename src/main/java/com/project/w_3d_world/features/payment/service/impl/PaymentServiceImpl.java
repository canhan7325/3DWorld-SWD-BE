package com.project.w_3d_world.features.payment.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PaymentResponse;
import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentAttemptRepository;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.payment.service.PaymentService;
import com.project.w_3d_world.features.payment.service.PaymentSourceResolver;
import com.project.w_3d_world.features.payment.service.ZaloPayClient;
import com.project.w_3d_world.features.payment.service.ZaloPayProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final DateTimeFormatter APP_TRANS_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final PaymentTransactionRepository transactions;
    private final PaymentAttemptRepository attempts;
    private final ZaloPayClient zaloPay;
    private final ZaloPayProperties properties;
    private final PaymentSourceResolver sourceResolver;

    @Override
    @Transactional
    public PaymentResponse create(int userId, CreatePaymentRequest request) {
        validateSource(request.sourceType());
        BigDecimal amount = sourceResolver.resolve(
                userId,
                request.sourceType(),
                request.sourceId(),
                request.stage(),
                request.amount());

        String operationKey = "ZALOPAY:" + userId + ":" + request.sourceType()
                + ":" + request.sourceId() + ":" + request.stage();
        PaymentTransaction transaction = transactions.findByOperationKey(operationKey)
                .orElseGet(() -> createTransaction(userId, request, operationKey, amount));
        PaymentAttempt attempt = new PaymentAttempt(
                transaction.getId(), operationKey + ":ATTEMPT:" + UUID.randomUUID(), "ZALOPAY");
        attempts.saveAndFlush(attempt);

        String appTransId = LocalDate.now(ZoneOffset.UTC).format(APP_TRANS_DATE)
                + "_" + transaction.getId();
        attempt.reference(appTransId);
        long appTime = System.currentTimeMillis();
        String embedData = "{\"redirecturl\":\"" + properties.callbackUrl() + "\"}";
        String item = "[]";
        int amountVnd = toAmount(amount);
        String signData = properties.appId() + "|" + appTransId + "|user-" + userId
                + "|" + amountVnd + "|" + appTime + "|" + embedData + "|" + item;

        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", properties.appId());
        payload.put("app_user", "user-" + userId);
        payload.put("app_trans_id", appTransId);
        payload.put("app_time", appTime);
        payload.put("amount", amountVnd);
        payload.put("description", request.description() == null
                ? "3DWorld payment" : request.description());
        payload.put("embed_data", embedData);
        payload.put("item", item);
        payload.put("callback_url", properties.callbackUrl());
        payload.put("mac", zaloPay.signCreateOrder(signData));

        Map<String, Object> response = zaloPay.createOrder(payload);
        if (!"1".equals(stringValue(response.getOrDefault("return_code", "0")))) {
            attempt.fail();
            transaction.markFailed();
        }

        return new PaymentResponse(
                transaction.getId(),
                attempt.getId(),
                appTransId,
                amount,
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
        PaymentAttempt attempt = attempts.findByExternalReferenceAndProviderName(
                        appTransId, "ZALOPAY")
                .orElseThrow(() -> ApiException.invalidRequest("Unknown ZaloPay transaction."));
        if ("SUCCESS".equals(attempt.getAttemptStatus())) {
            return;
        }

        PaymentTransaction transaction = transactions.findById(attempt.getTransactionId())
                .orElseThrow(() -> ApiException.invalidRequest("Unknown payment transaction."));
        long callbackAmount = parseLong(callback.get("amount"));
        if (!appTransId.equals(attempt.getExternalReference())
                || callbackAmount != toAmount(transaction.getAmount())) {
            throw ApiException.invalidRequest("ZaloPay callback does not match the payment.");
        }

        attempt.providerTransaction(stringValue(callback.get("zp_trans_id")));
        attempt.success();
        transaction.markSuccess();
    }

    @Override
    @Transactional
    public void reconcilePending() {
        for (PaymentAttempt attempt : attempts.findByAttemptStatusAndProviderName(
                "REQUESTED", "ZALOPAY")) {
            PaymentTransaction transaction = transactions.findById(attempt.getTransactionId())
                    .orElse(null);
            if (transaction == null || attempt.getExternalReference() == null) {
                continue;
            }

            Map<String, Object> result = zaloPay.queryOrder(attempt.getExternalReference());
            String returnCode = stringValue(result.get("return_code"));
            if ("1".equals(returnCode)) {
                attempt.success();
                transaction.markSuccess();
            } else if ("-1".equals(returnCode)) {
                attempt.fail();
                transaction.markFailed();
            }
        }
    }

    @Override
    @Transactional
    public PaymentResponse refund(int userId, int transactionId, BigDecimal amount, String reason) {
        PaymentTransaction original = transactions.findById(transactionId)
                .orElseThrow(() -> ApiException.invalidRequest("Payment transaction does not exist."));
        if (!userIdEquals(original, userId) || !"SUCCESS".equals(original.getStatus())) {
            throw ApiException.invalidRequest("Payment is not refundable by this user.");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(original.getAmount()) > 0) {
            throw ApiException.invalidRequest("Refund amount is invalid.");
        }
        PaymentAttempt attempt = attempts.findByTransactionIdAndProviderName(transactionId, "ZALOPAY")
                .orElseThrow(() -> ApiException.invalidRequest("Payment attempt does not exist."));
        if (attempt.getProviderTransactionId() == null) {
            throw ApiException.invalidRequest("ZaloPay transaction reference is unavailable.");
        }
        Map<String, Object> result = zaloPay.refundOrder(
                attempt.getProviderTransactionId(), toAmount(amount), reason);
        if (!"1".equals(stringValue(result.get("return_code")))) {
            throw ApiException.invalidRequest("ZaloPay refund was not accepted.");
        }
        return new PaymentResponse(
                original.getId(), attempt.getId(), attempt.getExternalReference(),
                amount, "REFUND_REQUESTED", null, null);
    }

    private PaymentTransaction createTransaction(
            int userId,
            CreatePaymentRequest request,
            String operationKey,
            BigDecimal amount) {
        PaymentTransaction transaction = new PaymentTransaction(
                request.sourceType(), operationKey, userId, request.stage(), amount);
        transaction.setSource(request.sourceType(), request.sourceId());
        return transactions.saveAndFlush(transaction);
    }

    private boolean userIdEquals(PaymentTransaction transaction, int userId) {
        return transaction.getPayerUserId() != null && transaction.getPayerUserId() == userId;
    }

    private void validateSource(String sourceType) {
        if (!Map.of(
                "PHYSICAL_ORDER", true,
                "RENTAL_CONTRACT", true,
                "PRINT_REQUEST", true,
                "SUBSCRIPTION", true,
                "WALLET", true,
                "PRINT_QUOTE", true).containsKey(sourceType)) {
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

    private long parseLong(Object value) {
        try {
            return Long.parseLong(stringValue(value));
        } catch (Exception exception) {
            throw ApiException.invalidRequest("Invalid payment amount.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCallback(String data) {
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(data, Map.class);
        } catch (Exception exception) {
            throw ApiException.invalidRequest("Invalid callback payload.");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
