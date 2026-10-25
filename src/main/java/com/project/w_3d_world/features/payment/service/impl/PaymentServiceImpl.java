package com.project.w_3d_world.features.payment.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.order.service.OrderPaymentService;
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
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final UserRepository users;
    private final OrderPaymentService orderPayments;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public PaymentResponse create(int userId, CreatePaymentRequest request) {
        users.lockById(userId).filter(user -> user.isActive()).orElseThrow(ApiException::invalidCredentials);
        validateSource(request.sourceType());
        if ("WALLET".equals(request.sourceType())
                && (request.idempotencyKey() == null || request.idempotencyKey().isBlank())) {
            throw ApiException.invalidRequest("A unique idempotencyKey is required for a top-up.");
        }
        BigDecimal amount = sourceResolver.resolve(
                userId,
                request.sourceType(),
                request.sourceId(),
                request.stage(),
                request.amount());

        String operationKey = "ZALOPAY:" + userId + ":" + request.sourceType()
                + ":" + request.sourceId() + ":" + request.stage()
                + ("WALLET".equals(request.sourceType()) ? ":" + request.idempotencyKey() : "");
        operationKey = HexFormat.of().formatHex(hashKey(operationKey));
        String stableOperationKey = operationKey;
        PaymentTransaction transaction = transactions.findByOperationKey(operationKey)
                .orElseGet(() -> createTransaction(userId, request, stableOperationKey, amount));
        if (transaction.getAmount().compareTo(amount) != 0) {
            throw ApiException.invalidRequest("Payment snapshot differs; create a new payment source.");
        }
        var previous = attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(
                transaction.getId(), "ZALOPAY");
        if ("SUCCESS".equals(transaction.getStatus()) || previous.filter(value ->
                "REQUESTED".equals(value.getAttemptStatus()) || "UNKNOWN".equals(value.getAttemptStatus()))
                .isPresent()) {
            var value = previous.orElseThrow();
            return new PaymentResponse(transaction.getId(), value.getId(), value.getExternalReference(),
                    transaction.getAmount(), transaction.getStatus(), value.getOrderUrl(), null);
        }
        PaymentAttempt attempt = new PaymentAttempt(
                transaction.getId(), "ZALOPAY-" + UUID.randomUUID(), "ZALOPAY");
        long appTime = System.currentTimeMillis();
        attempt.initializeProvider(previous.map(value -> value.getAttemptNumber() + 1).orElse(1),
                properties.appId(), appTime, Instant.now().plusSeconds(900));
        attempts.saveAndFlush(attempt);

        String appTransId = LocalDate.now(ZoneOffset.UTC).format(APP_TRANS_DATE)
                + "_" + attempt.getId();
        attempt.reference(appTransId);
        String redirectUrl = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(properties.redirectUrl())
                .queryParam("transactionId", transaction.getId()).build().toUriString();
        String embedData = JsonValues.write(Map.of("redirecturl", redirectUrl));
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
        if (properties.callbackUrl() != null && !properties.callbackUrl().isBlank()) {
            payload.put("callback_url", properties.callbackUrl());
        }
        payload.put("mac", zaloPay.signCreateOrder(signData));

        Map<String, Object> response;
        try {
            response = zaloPay.createOrder(payload);
        } catch (RuntimeException exception) {
            attempt.reconciliation("Provider create outcome is unknown; query before retrying.");
            return new PaymentResponse(transaction.getId(), attempt.getId(), appTransId,
                    amount, "UNKNOWN", null, null);
        }
        attempt.providerResult(stringValue(response.get("order_url")), stringValue(response.get("return_code")));
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
        PaymentTransaction transaction = transactions.lockById(attempt.getTransactionId())
                .orElseThrow(() -> ApiException.invalidRequest("Unknown payment transaction."));
        long callbackAmount = parseLong(callback.get("amount"));
        if (!appTransId.equals(attempt.getExternalReference())
                || callbackAmount != toAmount(transaction.getAmount())) {
            throw ApiException.invalidRequest("ZaloPay callback does not match the payment.");
        }

        if (parseLong(callback.get("app_id")) != properties.appId()) {
            throw ApiException.invalidRequest("ZaloPay app ID does not match.");
        }
        if ("SUCCESS".equals(attempt.getAttemptStatus())) {
            return;
        }
        attempt.recordEvent(JsonValues.write(callback));
        attempt.providerTransaction(stringValue(callback.get("zp_trans_id")));
        attempt.success();
        transaction.markSuccess();
        orderPayments.verified(transaction);
    }

    @Override
    @Transactional
    public PaymentResponse status(int userId, int transactionId) {
        // Check ownership before querying the provider; never trust the browser redirect's status.
        PaymentTransaction snapshot = transactions.findById(transactionId)
                .filter(value -> Integer.valueOf(userId).equals(value.getPayerUserId()))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Payment does not exist."));
        PaymentAttempt candidate = attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(
                snapshot.getId(), "ZALOPAY").orElseThrow(() -> ApiException.invalidRequest("Payment attempt does not exist."));
        // Same lock order as callback and reconciliation: attempt, then transaction.
        PaymentAttempt attempt = attempts.lockById(candidate.getId()).orElseThrow();
        PaymentTransaction transaction = transactions.lockById(transactionId).orElseThrow();
        queryAndApply(attempt, transaction);
        String status = "SUCCESS".equals(transaction.getStatus()) ? "SUCCESS"
                : attempt.getReconciliationReason() != null && !"FAILED".equals(attempt.getAttemptStatus())
                    ? "UNKNOWN" : transaction.getStatus();
        return new PaymentResponse(transaction.getId(), attempt.getId(), attempt.getExternalReference(),
                transaction.getAmount(), status, attempt.getOrderUrl(), null);
    }

    @Override
    @Transactional
    public void reconcilePending() {
        for (PaymentAttempt candidate : attempts.findByAttemptStatusAndProviderName("REQUESTED", "ZALOPAY")) {
            PaymentAttempt attempt = attempts.lockById(candidate.getId()).orElseThrow();
            PaymentTransaction transaction = transactions.lockById(attempt.getTransactionId()).orElse(null);
            if (transaction != null) queryAndApply(attempt, transaction);
        }
    }

    private void queryAndApply(PaymentAttempt attempt, PaymentTransaction transaction) {
        if ("SUCCESS".equals(transaction.getStatus()) || !"REQUESTED".equals(attempt.getAttemptStatus())
                || attempt.getExternalReference() == null) return;
        Map<String, Object> result;
        try {
            result = zaloPay.queryOrder(attempt.getExternalReference());
            attempt.queried();
        } catch (RuntimeException exception) {
            attempt.reconciliation("Provider query unavailable; retry later.");
            return;
        }
        String returnCode = stringValue(result.get("return_code"));
        if ("1".equals(returnCode)) {
            if (result.get("amount") == null
                    || parseLong(result.get("amount")) != toAmount(transaction.getAmount())
                    || result.get("zp_trans_id") == null) {
                attempt.reconciliation("Provider query payment details do not match.");
                return;
            }
            attempt.providerTransaction(stringValue(result.get("zp_trans_id")));
            attempt.recordEvent(JsonValues.write(result));
            attempt.success();
            transaction.markSuccess();
            orderPayments.verified(transaction);
        } else if ("2".equals(returnCode)) {
            // ZaloPay query: 1 = paid, 2 = failed, 3 = processing. Unknown codes stay unresolved.
            attempt.fail();
            transaction.markFailed();
        }
    }

    @Override
    @Transactional
    public PaymentResponse refund(int userId, int transactionId, BigDecimal amount, String reason) {
        // Keep the existing route, but never bypass the v2 refund request/review lifecycle.
        throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                "Direct refunds are not enabled. An approved refund request and settlement workflow are required.");
    }

    private PaymentTransaction createTransaction(
            int userId,
            CreatePaymentRequest request,
            String operationKey,
            BigDecimal amount) {
        PaymentTransaction transaction = new PaymentTransaction(
                request.sourceType(), operationKey, userId, request.stage(), amount);
        if ("PRINT_QUOTE".equals(request.sourceType())) {
            Integer printId = jdbc.queryForObject(
                    "SELECT print_request_id FROM print_quotes WHERE print_quote_id = ?",
                    Integer.class, request.sourceId());
            transaction.setSource("PRINT_REQUEST", printId);
            transaction.setSource("PRINT_QUOTE", request.sourceId());
        } else {
            transaction.setSource(request.sourceType(), request.sourceId());
        }
        return transactions.saveAndFlush(transaction);
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
    private byte[] hashKey(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
