package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStateResponse;
import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PrintRequestProductionService {
    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository items;
    private final PrintQuoteRepository quotes;
    private final PrintRequestStatusHistoryRepository histories;
    private final AuditLogRepository audits;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public PrintQuote requirePayableUpfrontQuote(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        return validateAcceptedCurrentQuote(request, request.getCurrentQuoteId());
    }

    @Transactional
    public PrintRequestStateResponse activateAfterVerifiedPayment(
            Integer requestId, Integer quoteId, Integer transactionId,
            String purpose, String transactionStatus, BigDecimal paidAmount) {
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (transactionId == null || transactionId <= 0) {
            throw ApiException.invalidRequest("Verified payment transactionId is required.");
        }
        if (!"POD".equals(purpose) || !"SUCCESS".equals(transactionStatus)) {
            throw conflict("Only verified successful POD payments can activate production.");
        }
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.invalidRequest("Verified paidAmount must be positive.");
        }

        PrintRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())) {
            throw conflict("Cancelled Print Request cannot enter production queue.");
        }

        PrintQuote currentQuote = validateAcceptedCurrentQuoteForProduction(request, quoteId);
        if (paidAmount.compareTo(currentQuote.getTotalAmount()) != 0) {
            throw conflict("Verified payment amount must cover 100% of the accepted quote total.");
        }

        if (request.getLockedAt() != null && "IN_QUEUE".equals(request.getStatus())) {
            return state(request);
        }

        Instant now = Instant.now();
        String fromStatus = request.getStatus();
        request.lockForProduction(now);
        requests.saveAndFlush(request);

        List<PrintRequestItem> requestItems = items
                .findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (requestItems.isEmpty()) {
            throw conflict("Print Request has no items to queue for production.");
        }
        for (PrintRequestItem item : requestItems) {
            item.queueForProduction();
            items.saveAndFlush(item);
        }

        String sourceEventId = "POD_PAYMENT_VERIFIED:" + requestId + ":tx:" + transactionId;
        histories.saveAndFlush(new PrintRequestStatusHistory(
                requestId, fromStatus, "IN_QUEUE", request.getCustomerUserId(),
                "Verified 100% upfront POD payment", sourceEventId));

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("printQuoteId", currentQuote.getPrintQuoteId());
        auditData.put("transactionId", transactionId);
        auditData.put("paidAmount", paidAmount);
        auditData.put("fileVersionSnapshot", currentQuote.getFileVersionSnapshot());
        auditData.put("specificationVersionSnapshot", currentQuote.getSpecificationVersionSnapshot());
        auditData.put("lockedAt", now.toString());
        audits.saveAndFlush(new AuditLog(request.getCustomerUserId(), "POD_PRODUCTION_QUEUED",
                "PRINT_REQUEST", requestId, null, json.writeValueAsString(auditData)));

        return state(request);
    }

    private PrintQuote validateAcceptedCurrentQuote(PrintRequest request, Integer expectedQuoteId) {
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())) {
            throw conflict("Print Request is cancelled.");
        }
        if (request.getLockedAt() != null || "IN_QUEUE".equals(request.getStatus())) {
            throw conflict("Print Request is already locked for production.");
        }
        return validateAcceptedCurrentQuoteForProduction(request, expectedQuoteId);
    }

    private PrintQuote validateAcceptedCurrentQuoteForProduction(
            PrintRequest request, Integer expectedQuoteId) {
        Integer currentQuoteId = request.getCurrentQuoteId();
        if (currentQuoteId == null) {
            throw conflict("Print Request has no active current quote.");
        }
        if (expectedQuoteId != null && !expectedQuoteId.equals(currentQuoteId)) {
            throw conflict("Target quote does not match the current Print Request quote.");
        }
        PrintQuote quote = quotes.findByPrintQuoteIdAndPrintRequestId(
                currentQuoteId, request.getPrintRequestId())
                .orElseThrow(() -> conflict("Current Print Quote was not found."));
        if (!"ACCEPTED".equals(quote.getQuoteStatus())) {
            throw conflict("Current Print Quote must be ACCEPTED before payment or production.");
        }
        if (quote.getFileVersionSnapshot() == null
                || !quote.getFileVersionSnapshot().equals(request.getCurrentFileVersion())
                || quote.getSpecificationVersionSnapshot() == null
                || !quote.getSpecificationVersionSnapshot().equals(request.getCurrentSpecVersion())) {
            throw conflict("Accepted quote versions do not match current Print Request versions.");
        }
        if (quote.getTotalAmount() == null || quote.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0
                || quote.getDepositAmount() == null
                || quote.getDepositAmount().compareTo(quote.getTotalAmount()) != 0
                || quote.getRemainingAmount() == null
                || quote.getRemainingAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw conflict("Accepted quote does not satisfy the 100% upfront payment policy.");
        }
        return quote;
    }

    private static PrintRequestStateResponse state(PrintRequest request) {
        return new PrintRequestStateResponse(
                request.getPrintRequestId(), request.getStatus(),
                request.isCopyrightConfirmed(), request.isPreviewConfirmed(),
                request.getCurrentFileVersion(), request.getCurrentSpecVersion(),
                request.getSubmittedAt(), request.getCancelledAt(), request.getUpdatedAt());
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
