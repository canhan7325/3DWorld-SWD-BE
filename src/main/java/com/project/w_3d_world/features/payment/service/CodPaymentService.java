package com.project.w_3d_world.features.payment.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.JsonValues;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.payment.dto.CodDtos.*;
import com.project.w_3d_world.features.payment.entity.*;
import com.project.w_3d_world.features.payment.repository.*;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** COD cash facts only. Wallet, earnings release and refund adjudication remain separate workflows. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodPaymentService {
    private final PhysicalOrderRepository orders;
    private final PaymentTransactionRepository transactions;
    private final PaymentAttemptRepository attempts;
    private final StorefrontRepository stores;
    private final AccountAuthorization authorization;
    private final AuditLogRepository audit;
    private final ObjectMapper mapper;

    @Transactional
    public void create(PhysicalOrder order) {
        if (!order.isCod() || order.getTotalAmount().signum() <= 0) {
            throw ApiException.invalidRequest("COD requires a physical order with a positive total.");
        }
        String key = operationKey(order.getId());
        if (transactions.findByOperationKey(key).isPresent()) return;
        var payment = new PaymentTransaction("PHYSICAL_ORDER", key, order.getBuyerUserId(), "FULL", order.getTotalAmount());
        payment.setSource("PHYSICAL_ORDER", order.getId());
        payment.initializeCod();
        transactions.saveAndFlush(payment);
        var attempt = new PaymentAttempt(payment.getId(), key, "COD");
        attempt.initializeCod();
        attempts.saveAndFlush(attempt);
    }

    public CodResponse get(int actor, int orderId) {
        var order = orders.findById(orderId).orElseThrow(this::missing);
        if (!order.getBuyerUserId().equals(actor)
                && stores.findByOwnerUserId(actor).filter(store -> store.getId().equals(order.getStorefrontId())).isEmpty()) {
            authorization.requireRole(actor, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        }
        var payment = payment(order);
        return response(order, payment, attempt(payment));
    }

    @Transactional
    public void cancel(PhysicalOrder order) {
        if (!order.isCod()) return;
        var payment = payment(order);
        if ("CANCELLED".equals(payment.getStatus())) return;
        var attempt = attempt(payment);
        if (attempt.getCodCollectedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Collected COD requires the refund workflow.");
        }
        payment.cancelCod();
        attempt.cancelCod();
    }

    @Transactional
    public CodResponse collect(int actor, int orderId, EvidenceRequest request) {
        authorization.requireRole(actor, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        var order = orders.lockById(orderId).orElseThrow(this::missing);
        rejectInterestedReviewer(actor, order);
        var payment = payment(order);
        var attempt = attempt(payment);
        validateAmountAndTime(order, request);
        if (attempt.getCodCollectedAt() != null) {
            requireReplay(attempt, "collection", request);
            return response(order, payment, attempt);
        }
        if (!Set.of("DELIVERED", "RECEIVED").contains(order.getStatus()) || order.getDeliveredAt() == null
                || request.occurredAt().isBefore(order.getDeliveredAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "COD collection requires a delivered order and collection time at or after delivery.");
        }
        if (!"PENDING".equals(payment.getStatus()) || !"REQUESTED".equals(attempt.getAttemptStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COD payment cannot be collected in its current state.");
        }
        attempt.collectCod(request.occurredAt(), request.reference().trim(), request.evidenceKey(), actor, request.reason());
        payment.collectedCod();
        record(attempt, actor, "collection", request);
        audit.save(new AuditLog(actor, "COD_COLLECTED", "PHYSICAL_ORDER", orderId, request.reason(),
                JsonValues.write(Map.of("amount", request.amount(), "reference", request.reference(), "evidenceKey", request.evidenceKey()))));
        attempts.flush();
        return response(order, payment, attempt);
    }

    @Transactional
    public CodResponse settle(int actor, int orderId, EvidenceRequest request) {
        authorization.requireRole(actor, RoleCode.ADMIN);
        var order = orders.lockById(orderId).orElseThrow(this::missing);
        rejectInterestedReviewer(actor, order);
        var payment = payment(order);
        var attempt = attempt(payment);
        validateAmountAndTime(order, request);
        if (attempt.getCodSettledAt() != null) {
            requireReplay(attempt, "settlement", request);
            return response(order, payment, attempt);
        }
        if (attempt.getCodCollectedAt() == null || request.occurredAt().isBefore(attempt.getCodCollectedAt())
                || !"PROCESSING".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COD must be collected before recording settlement to the platform.");
        }
        attempt.settleCod(request.occurredAt(), request.reference().trim(), request.evidenceKey(), actor, request.reason());
        payment.markSuccess();
        payment.fulfillment("SETTLED");
        record(attempt, actor, "settlement", request);
        audit.save(new AuditLog(actor, "COD_SETTLED", "PHYSICAL_ORDER", orderId, request.reason(),
                JsonValues.write(Map.of("amount", request.amount(), "reference", request.reference(), "evidenceKey", request.evidenceKey()))));
        attempts.flush();
        // Does not mark the order PAID or financially complete, recognize commission or credit a wallet.
        return response(order, payment, attempt);
    }

    private PaymentTransaction payment(PhysicalOrder order) {
        if (!order.isCod()) throw ApiException.invalidRequest("This order does not use COD.");
        return transactions.findByOperationKey(operationKey(order.getId())).orElseThrow(this::missing);
    }

    private PaymentAttempt attempt(PaymentTransaction payment) {
        return attempts.findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(payment.getId(), "COD_MANUAL")
                .orElseThrow(this::missing);
    }

    private void validateAmountAndTime(PhysicalOrder order, EvidenceRequest request) {
        if (request.amount().compareTo(order.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "COD amount must match the full order total including shipping. Partial or mismatched amounts require reconciliation.");
        }
        if (request.occurredAt().isAfter(Instant.now())) throw ApiException.invalidRequest("COD event cannot be in the future.");
    }

    private void rejectInterestedReviewer(int actor, PhysicalOrder order) {
        if (order.getBuyerUserId().equals(actor) || stores.findByOwnerUserId(actor)
                .filter(store -> store.getId().equals(order.getStorefrontId())).isPresent()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot verify COD cash facts for your own purchase or storefront.");
        }
    }

    private void record(PaymentAttempt attempt, int actor, String event, EvidenceRequest request) {
        var data = attempt.getProviderEventSnapshot() == null ? mapper.createObjectNode()
                : (ObjectNode) mapper.readTree(attempt.getProviderEventSnapshot());
        var value = mapper.createObjectNode();
        value.put("amount", request.amount().toPlainString());
        value.put("reference", request.reference().trim());
        value.put("evidenceKey", request.evidenceKey());
        value.put("occurredAt", request.occurredAt().toString());
        value.put("actorId", actor);
        value.put("reason", request.reason());
        data.set(event, value);
        attempt.recordEvent(mapper.writeValueAsString(data));
    }

    private void requireReplay(PaymentAttempt attempt, String event, EvidenceRequest request) {
        var prior = mapper.readTree(attempt.getProviderEventSnapshot()).path(event);
        if (!prior.path("reference").asText().equals(request.reference().trim())
                || new BigDecimal(prior.path("amount").asText()).compareTo(request.amount()) != 0
                || !prior.path("occurredAt").asText().equals(request.occurredAt().toString())
                || !prior.path("evidenceKey").asText().equals(request.evidenceKey())) {
            throw new ApiException(HttpStatus.CONFLICT, "COD event was already recorded with different evidence.");
        }
    }

    private CodResponse response(PhysicalOrder order, PaymentTransaction payment, PaymentAttempt attempt) {
        boolean outstanding = attempt.getCodCollectedAt() == null && !"CANCELLED".equals(payment.getStatus());
        return new CodResponse(order.getId(), payment.getId(), "COD", payment.getStatus(), attempt.getGatewayState(),
                payment.getAmount(), outstanding ? payment.getAmount() : BigDecimal.ZERO,
                attempt.getCodCollectedAt(), attempt.getCodSettledAt(), attempt.getExternalReference(), attempt.getProviderTransactionId());
    }

    private String operationKey(int orderId) {
        return "COD:PHYSICAL_ORDER:" + orderId;
    }
    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "COD order or payment does not exist.");
    }
}
