package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.payment.entity.PaymentAttempt;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentAttemptRepository;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStateResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PrintRequestPaymentFulfillmentService {
    private final PaymentTransactionRepository transactions;
    private final PaymentAttemptRepository attempts;
    private final PrintRequestRepository requests;
    private final PrintRequestStatusHistoryRepository histories;
    private final PrintRequestProductionService production;
    private final ObjectMapper json;

    @Transactional
    public PrintRequestStateResponse handleVerifiedSuccess(Integer transactionId) {
        if (transactionId == null || transactionId <= 0) {
            throw ApiException.invalidRequest("A positive payment transaction ID is required.");
        }
        PaymentTransaction payment = transactions.findById(transactionId)
                .orElseThrow(() -> conflict("POD payment transaction is unavailable."));
        if (payment.getPrintRequestId() == null || payment.getPrintRequestId() <= 0) {
            throw conflict("POD payment has no Print Request source.");
        }

        PrintRequest request = requests.findByIdForUpdate(payment.getPrintRequestId())
                .orElseThrow(() -> conflict("POD payment Print Request is unavailable."));
        payment = transactions.findById(transactionId)
                .orElseThrow(() -> conflict("POD payment transaction is unavailable."));
        if (!"PAYMENT".equals(payment.getTransactionType())
                || !"POD".equals(payment.getPurpose())
                || !"FULL".equals(payment.getPaymentStage())
                || !"PLATFORM".equals(payment.getSettlementMode())
                || !"VND".equals(payment.getCurrency())
                || !"SUCCESS".equals(payment.getStatus())
                || payment.getCompletedAt() == null
                || !request.getPrintRequestId().equals(payment.getPrintRequestId())
                || !request.getCustomerUserId().equals(payment.getPayerUserId())
                || payment.getPrintQuoteId() == null) {
            throw conflict("POD payment is not a verified FULL payment for this request.");
        }

        PaymentAttempt attempt = attempts
                .findFirstByTransactionIdAndProviderNameOrderByAttemptNumberDesc(transactionId, "ZALOPAY")
                .orElseThrow(() -> conflict("POD payment has no verified provider attempt."));
        if (!transactionId.equals(attempt.getTransactionId())
                || !"ZALOPAY".equals(attempt.getProviderName())
                || !"SUCCESS".equals(attempt.getAttemptStatus())
                || attempt.getVerifiedAt() == null
                || attempt.getExternalReference() == null || attempt.getExternalReference().isBlank()
                || attempt.getProviderTransactionId() == null || attempt.getProviderTransactionId().isBlank()
                || attempt.getProviderEventSnapshot() == null || attempt.getProviderEventSnapshot().isBlank()) {
            throw conflict("POD payment has no verified provider success record.");
        }
        verifyProviderSnapshot(payment, attempt);
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())) {
            throw conflict("Late payment success requires reconciliation; cancelled request remains cancelled.");
        }
        if (request.getLockedAt() != null && "IN_QUEUE".equals(request.getStatus())) {
            String eventId = "POD_PAYMENT_VERIFIED:" + request.getPrintRequestId() + ":tx:" + transactionId;
            boolean samePayment = histories
                    .findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(request.getPrintRequestId())
                    .stream().anyMatch(history -> eventId.equals(history.getSourceEventId()));
            if (!samePayment) throw conflict("Print Request was queued by a different payment.");
        } else if (request.getLockedAt() != null || !"QUOTE_ACCEPTED".equals(request.getStatus())) {
            throw conflict("Print Request is not awaiting verified payment.");
        }

        return production.activateAfterVerifiedPayment(request.getPrintRequestId(),
                payment.getPrintQuoteId(), transactionId, payment.getPurpose(),
                payment.getStatus(), payment.getAmount());
    }

    private void verifyProviderSnapshot(PaymentTransaction payment, PaymentAttempt attempt) {
        JsonNode event;
        try {
            event = json.readTree(attempt.getProviderEventSnapshot());
        } catch (RuntimeException exception) {
            throw conflict("POD provider verification snapshot is invalid.");
        }
        BigDecimal recordedAmount;
        try {
            recordedAmount = new BigDecimal(event.path("amount").asText());
        } catch (RuntimeException exception) {
            throw conflict("POD provider verification snapshot has no valid amount.");
        }
        if (!event.isObject() || recordedAmount.compareTo(payment.getAmount()) != 0
                || !attempt.getProviderTransactionId().equals(event.path("zp_trans_id").asText())
                || (!event.path("app_trans_id").isMissingNode()
                        && !attempt.getExternalReference().equals(event.path("app_trans_id").asText()))
                || (!event.path("app_id").isMissingNode() && attempt.getAppId() != null
                        && event.path("app_id").asInt() != attempt.getAppId())) {
            throw conflict("POD provider verification snapshot does not match the payment.");
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
