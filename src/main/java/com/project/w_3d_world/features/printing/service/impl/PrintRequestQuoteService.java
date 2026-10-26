package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintQuoteRevisionRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteResponse;
import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintQuoteItem;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.entity.PrintRequestTechnicalReview;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.exception.QuoteExpiredException;
import com.project.w_3d_world.features.printing.repository.PrintQuoteItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestTechnicalReviewRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestWorkflowPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PrintRequestQuoteService {
    public static final Duration QUOTE_VALIDITY_WINDOW = Duration.ofHours(24);
    public static final String QUOTE_EXPIRED_REASON = "Quote expired after 24-hour deadline.";
    private static final String REVISION_START_PREFIX = "POD_POST_QUOTE_REVISION_START:";
    private static final String REVISION_SUBMIT_PREFIX = "POD_POST_QUOTE_REVISION:";

    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository requestItems;
    private final PrintRequestFileVersionRepository fileVersions;
    private final PrintRequestSpecVersionRepository specVersions;
    private final PrintQuoteRepository quotes;
    private final PrintQuoteItemRepository quoteItems;
    private final PrintRequestTechnicalReviewRepository reviews;
    private final PrintRequestStatusHistoryRepository histories;
    private final AuditLogRepository audits;
    private final PrintRequestReviewPolicy reviewPolicy;
    private final ObjectMapper json;

    @Transactional(noRollbackFor = QuoteExpiredException.class)
    public PrintQuoteResponse issueQuote(Integer staffId, Integer requestId,
            CreatePrintQuoteRequest input) {
        PrintRequestValidation.validateCustomerId(staffId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        validateQuoteInput(input);

        PrintRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())
                || request.getLockedAt() != null || request.getSubmittedAt() == null) {
            throw conflict("Print Request is not available for quotation.");
        }

        List<PrintQuote> existingQuotes = quotes.findByPrintRequestIdOrderByVersionNumberAsc(requestId);
        Instant now = Instant.now();
        if (!existingQuotes.isEmpty()) {
            if (existingQuotes.stream().anyMatch(q -> "ACCEPTED".equals(q.getQuoteStatus()))) {
                throw conflict("Print Request already has an accepted quote.");
            }
            Instant deadline = canonicalDeadline(existingQuotes.getFirst());
            if (!PrintRequestWorkflowPolicy.beforeQuoteDeadline(now, deadline)) {
                expireAndCancelLocked(request, existingQuotes, now);
                throw new QuoteExpiredException("Print Request quote 24-hour deadline has expired.");
            }
        }

        if (!"SUBMITTED".equals(request.getStatus()) || request.getCurrentQuoteId() != null) {
            throw conflict("Print Request is not in a quotable SUBMITTED state.");
        }
        if (!input.expectedFileVersion().equals(request.getCurrentFileVersion())
                || !input.expectedSpecVersion().equals(request.getCurrentSpecVersion())) {
            throw conflict("Print Request versions have changed.");
        }
        reviewPolicy.requireCurrentApproved(request);

        PrintRequestFileVersion currentFile = fileVersions.findByPrintRequestIdAndVersion(
                requestId, input.expectedFileVersion())
                .orElseThrow(() -> conflict("Current file version is unavailable."));
        PrintRequestSpecVersion currentSpec = specVersions.findByPrintRequestIdAndVersion(
                requestId, input.expectedSpecVersion())
                .orElseThrow(() -> conflict("Current specification version is unavailable."));
        List<PrintRequestItem> persistedItems = requestItems
                .findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (persistedItems.isEmpty() || persistedItems.size() != input.items().size()) {
            throw ApiException.invalidRequest("Quote items must match all Print Request items.");
        }

        Map<Integer, PrintRequestItem> itemById = persistedItems.stream()
                .collect(Collectors.toMap(PrintRequestItem::getPrintRequestItemId, Function.identity()));
        Map<Integer, JsonNode> specItemNodes = extractSpecItemsById(currentSpec.getSpecificationData());
        Set<Integer> seenItemIds = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        List<PreparedQuoteLine> preparedLines = new ArrayList<>(input.items().size());

        for (CreatePrintQuoteItemRequest lineInput : input.items()) {
            Integer reqItemId = lineInput.printRequestItemId();
            if (!seenItemIds.add(reqItemId) || !itemById.containsKey(reqItemId)) {
                throw ApiException.invalidRequest("Quote items must match all Print Request items.");
            }
            BigDecimal materialCost = normalizeNonNegativeAmount(
                    lineInput.materialCostSnapshot(), "materialCostSnapshot");
            BigDecimal laborCost = normalizeNonNegativeAmount(
                    lineInput.laborCostSnapshot(), "laborCostSnapshot");
            BigDecimal optionFee = lineInput.optionFeeSnapshot() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)
                    : normalizeNonNegativeAmount(lineInput.optionFeeSnapshot(), "optionFeeSnapshot");
            BigDecimal lineTotal = materialCost.add(laborCost).add(optionFee)
                    .setScale(2, RoundingMode.UNNECESSARY);
            totalAmount = totalAmount.add(lineTotal).setScale(2, RoundingMode.UNNECESSARY);
            PrintRequestItem persistedItem = itemById.get(reqItemId);
            String specSnapshot = buildItemSpecificationSnapshot(
                    persistedItem, specItemNodes.get(reqItemId));
            preparedLines.add(new PreparedQuoteLine(
                    reqItemId, persistedItem.getQuantity(), specSnapshot,
                    materialCost, laborCost, optionFee, lineTotal));
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.invalidRequest("Quote totalAmount must be greater than zero.");
        }
        if (input.etaAt() != null && !input.etaAt().isAfter(now)) {
            throw ApiException.invalidRequest("Quote etaAt must be in the future.");
        }

        for (PrintQuote prior : existingQuotes) {
            if ("ISSUED".equals(prior.getQuoteStatus())) {
                prior.supersede();
                quotes.saveAndFlush(prior);
            }
        }

        int versionNumber = existingQuotes.isEmpty()
                ? 1
                : existingQuotes.getLast().getVersionNumber() + 1;
        Instant expiresAt = existingQuotes.isEmpty()
                ? now.plus(QUOTE_VALIDITY_WINDOW)
                : canonicalDeadline(existingQuotes.getFirst());

        PrintQuote quote = PrintQuote.issueUpfront(
                requestId, staffId, versionNumber,
                input.expectedFileVersion(), input.expectedSpecVersion(),
                totalAmount, input.etaAt(), now, expiresAt);
        PrintQuote savedQuote = quotes.saveAndFlush(quote);
        if (savedQuote.getPrintQuoteId() == null) {
            throw new IllegalStateException("Print Quote ID was not generated.");
        }

        List<PrintQuoteItem> savedItems = new ArrayList<>(preparedLines.size());
        for (PreparedQuoteLine line : preparedLines) {
            PrintQuoteItem savedLine = quoteItems.saveAndFlush(new PrintQuoteItem(
                    savedQuote.getPrintQuoteId(), line.printRequestItemId(),
                    input.expectedFileVersion(), input.expectedSpecVersion(),
                    currentFile.getFileUrl(), line.specificationSnapshot(),
                    line.quantitySnapshot(), line.materialCost(),
                    line.laborCost(), line.optionFee(), line.lineTotal()));
            savedItems.add(savedLine);
        }

        String fromStatus = request.getStatus();
        request.markQuoted(savedQuote.getPrintQuoteId(), staffId, now);
        requests.saveAndFlush(request);
        if (!"QUOTED".equals(fromStatus)) {
            histories.saveAndFlush(new PrintRequestStatusHistory(
                    requestId, fromStatus, "QUOTED", staffId, null,
                    "POD_QUOTE_ISSUED:" + requestId + ":v" + versionNumber));
        }

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("printQuoteId", savedQuote.getPrintQuoteId());
        auditData.put("versionNumber", versionNumber);
        auditData.put("fileVersionSnapshot", input.expectedFileVersion());
        auditData.put("specificationVersionSnapshot", input.expectedSpecVersion());
        auditData.put("totalAmount", savedQuote.getTotalAmount());
        auditData.put("depositAmount", savedQuote.getDepositAmount());
        auditData.put("remainingAmount", savedQuote.getRemainingAmount());
        auditData.put("issuedAt", savedQuote.getIssuedAt().toString());
        auditData.put("expiresAt", savedQuote.getExpiresAt().toString());
        audits.saveAndFlush(new AuditLog(staffId, "POD_QUOTE_ISSUED", "PRINT_REQUEST",
                requestId, null, json.writeValueAsString(auditData)));

        int revisionsUsed = countPostQuoteRevisions(requestId);
        return toResponse(request, savedQuote, savedItems, revisionsUsed);
    }

    @Transactional(noRollbackFor = QuoteExpiredException.class)
    public PrintQuoteResponse requestRevision(Integer customerId, Integer requestId,
            Integer quoteId, RequestPrintQuoteRevisionRequest input) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        validateQuoteId(quoteId);
        if (input == null || input.expectedFileVersion() == null
                || input.expectedFileVersion() <= 0 || input.expectedSpecVersion() == null
                || input.expectedSpecVersion() <= 0) {
            throw ApiException.invalidRequest("Expected file and specification versions are required.");
        }
        if (input.reason() == null || input.reason().isBlank()
                || input.reason().trim().length() > 10_000
                || input.reason().chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalidRequest("A meaningful revision reason is required.");
        }

        PrintRequest request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())) {
            throw conflict("Cancelled Print Request cannot be revised.");
        }
        if (request.getLockedAt() != null || "IN_QUEUE".equals(request.getStatus())) {
            throw conflict("Locked Print Request in production queue cannot be revised.");
        }

        List<PrintQuote> existingQuotes = quotes.findByPrintRequestIdOrderByVersionNumberAsc(requestId);
        if (existingQuotes.isEmpty()) {
            throw conflict("Print Request has no issued quote to revise.");
        }

        PrintQuote quote = quotes.findByPrintQuoteIdAndPrintRequestId(quoteId, requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Quote was not found."));
        if ("ACCEPTED".equals(quote.getQuoteStatus())
                || "QUOTE_ACCEPTED".equals(request.getStatus())
                || existingQuotes.stream().anyMatch(q -> "ACCEPTED".equals(q.getQuoteStatus()))) {
            throw conflict("Accepted quote with payment in progress cannot be revised.");
        }

        Instant now = Instant.now();
        Instant deadline = canonicalDeadline(existingQuotes.getFirst());
        if (!PrintRequestWorkflowPolicy.beforeQuoteDeadline(now, deadline)
                || !PrintRequestWorkflowPolicy.beforeQuoteDeadline(now, quote.getExpiresAt())) {
            expireAndCancelLocked(request, existingQuotes, now);
            throw new QuoteExpiredException("Print Request quote 24-hour deadline has expired.");
        }

        if (!quoteId.equals(request.getCurrentQuoteId())
                || !"ISSUED".equals(quote.getQuoteStatus())
                || !"QUOTED".equals(request.getStatus())) {
            throw conflict("Only the current active quote can be revised.");
        }
        if (!input.expectedFileVersion().equals(request.getCurrentFileVersion())
                || !input.expectedSpecVersion().equals(request.getCurrentSpecVersion())) {
            throw conflict("Print Request versions have changed.");
        }

        int revisionsUsed = countPostQuoteRevisions(requestId);
        if (revisionsUsed >= PrintRequestReviewPolicy.MAX_POST_QUOTE_REVISIONS) {
            throw conflict("Buyer revision limit of 3 post-quote revisions has been reached.");
        }

        int nextRevisionNumber = revisionsUsed + 1;
        String reason = input.reason().trim();

        quote.supersede();
        quotes.saveAndFlush(quote);

        String fromStatus = request.getStatus();
        request.requireRevision();
        requests.saveAndFlush(request);

        Map<String, Object> reviewPayload = new LinkedHashMap<>();
        reviewPayload.put("outcome", "CORRECTION_REQUESTED");
        reviewPayload.put("source", "BUYER_REVISION");
        reviewPayload.put("revisionNumber", nextRevisionNumber);
        reviewPayload.put("quoteDeadline", deadline.toString());
        reviewPayload.put("reviewedFileVersion", input.expectedFileVersion());
        reviewPayload.put("reviewedSpecVersion", input.expectedSpecVersion());
        reviewPayload.put("reason", reason);
        reviews.saveAndFlush(new PrintRequestTechnicalReview(
                requestId, customerId, json.writeValueAsString(reviewPayload)));

        histories.saveAndFlush(new PrintRequestStatusHistory(
                requestId, fromStatus, "REQUIRES_REVISION", customerId, reason,
                REVISION_START_PREFIX + requestId + ":" + nextRevisionNumber));

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("supersededQuoteId", quoteId);
        auditData.put("revisionNumber", nextRevisionNumber);
        auditData.put("remainingRevisions",
                PrintRequestReviewPolicy.MAX_POST_QUOTE_REVISIONS - revisionsUsed);
        auditData.put("quoteDeadline", deadline.toString());
        audits.saveAndFlush(new AuditLog(customerId, "POD_BUYER_REVISION_REQUESTED",
                "PRINT_REQUEST", requestId, reason, json.writeValueAsString(auditData)));

        List<PrintQuoteItem> items = quoteItems
                .findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(quoteId);
        return toResponse(request, quote, items, revisionsUsed);
    }

    @Transactional(noRollbackFor = QuoteExpiredException.class)
    public PrintQuoteResponse acceptQuote(Integer customerId, Integer requestId, Integer quoteId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        validateQuoteId(quoteId);

        PrintRequest request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())) {
            throw conflict("Cancelled Print Request quote cannot be accepted.");
        }
        if (request.getLockedAt() != null || "IN_QUEUE".equals(request.getStatus())) {
            throw conflict("Print Request is already locked for production.");
        }

        List<PrintQuote> existingQuotes = quotes.findByPrintRequestIdOrderByVersionNumberAsc(requestId);
        if (existingQuotes.isEmpty()) {
            throw conflict("Print Request has no issued quote.");
        }

        PrintQuote quote = quotes.findByPrintQuoteIdAndPrintRequestId(quoteId, requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Quote was not found."));
        Instant now = Instant.now();
        Instant deadline = canonicalDeadline(existingQuotes.getFirst());
        if (!existingQuotes.stream().anyMatch(q -> "ACCEPTED".equals(q.getQuoteStatus()))
                && (!PrintRequestWorkflowPolicy.beforeQuoteDeadline(now, deadline)
                    || !PrintRequestWorkflowPolicy.beforeQuoteDeadline(now, quote.getExpiresAt()))) {
            expireAndCancelLocked(request, existingQuotes, now);
            throw new QuoteExpiredException("Print Request quote 24-hour deadline has expired.");
        }

        if (!quoteId.equals(request.getCurrentQuoteId())
                || !"ISSUED".equals(quote.getQuoteStatus())
                || !"QUOTED".equals(request.getStatus())) {
            throw conflict("Only the current valid ISSUED quote can be accepted.");
        }
        if (quote.getFileVersionSnapshot() == null
                || !quote.getFileVersionSnapshot().equals(request.getCurrentFileVersion())
                || quote.getSpecificationVersionSnapshot() == null
                || !quote.getSpecificationVersionSnapshot().equals(request.getCurrentSpecVersion())) {
            throw conflict("Quote snapshot does not match current Print Request versions.");
        }
        if (quote.getTotalAmount() == null || quote.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0
                || quote.getDepositAmount() == null
                || quote.getDepositAmount().compareTo(quote.getTotalAmount()) != 0
                || quote.getRemainingAmount() == null
                || quote.getRemainingAmount().compareTo(BigDecimal.ZERO) != 0) {
            throw conflict("Quote does not satisfy the 100% upfront payment policy.");
        }

        quote.accept(now);
        quotes.saveAndFlush(quote);

        String fromStatus = request.getStatus();
        request.markQuoteAccepted(quoteId, now);
        requests.saveAndFlush(request);
        histories.saveAndFlush(new PrintRequestStatusHistory(
                requestId, fromStatus, "QUOTE_ACCEPTED", customerId, null,
                "POD_QUOTE_ACCEPTED:" + requestId + ":q" + quoteId));

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("printQuoteId", quoteId);
        auditData.put("versionNumber", quote.getVersionNumber());
        auditData.put("totalAmount", quote.getTotalAmount());
        auditData.put("acceptedAt", now.toString());
        audits.saveAndFlush(new AuditLog(customerId, "POD_QUOTE_ACCEPTED",
                "PRINT_REQUEST", requestId, null, json.writeValueAsString(auditData)));

        int revisionsUsed = countPostQuoteRevisions(requestId);
        List<PrintQuoteItem> items = quoteItems
                .findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(quoteId);
        return toResponse(request, quote, items, revisionsUsed);
    }

    @Transactional
    public boolean expireIfOverdue(Integer requestId) {
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (request.getCancelledAt() != null || "CANCELLED".equals(request.getStatus())
                || request.getLockedAt() != null || "IN_QUEUE".equals(request.getStatus())) {
            return false;
        }
        List<PrintQuote> existingQuotes = quotes.findByPrintRequestIdOrderByVersionNumberAsc(requestId);
        if (existingQuotes.isEmpty()) {
            return false;
        }
        // Do not misclassify an accepted quote awaiting payment as an unaccepted expired quote.
        if ("QUOTE_ACCEPTED".equals(request.getStatus())
                || existingQuotes.stream().anyMatch(q -> "ACCEPTED".equals(q.getQuoteStatus()))) {
            return false;
        }
        Instant now = Instant.now();
        Instant deadline = canonicalDeadline(existingQuotes.getFirst());
        if (PrintRequestWorkflowPolicy.beforeQuoteDeadline(now, deadline)) {
            return false;
        }
        expireAndCancelLocked(request, existingQuotes, now);
        return true;
    }

    @Transactional(readOnly = true)
    public List<PrintQuoteResponse> listCustomerQuotes(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        return listQuotesForRequest(request);
    }

    @Transactional(readOnly = true)
    public List<PrintQuoteResponse> listStaffQuotes(Integer requestId) {
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findById(requestId)
                .orElseThrow(PrintingErrors::requestNotFound);
        return listQuotesForRequest(request);
    }

    public int countPostQuoteRevisions(Integer requestId) {
        Set<Integer> indices = new HashSet<>();
        String submitPrefix = REVISION_SUBMIT_PREFIX + requestId + ":";
        // Opening a correction cycle reserves it; only a successful resubmission uses quota.
        for (PrintRequestStatusHistory row : histories
                .findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(requestId)) {
            String eventId = row.getSourceEventId();
            if (eventId == null) continue;
            if (eventId.startsWith(submitPrefix)) {
                parseRevisionIndex(eventId.substring(submitPrefix.length()), indices);
            }
        }
        return indices.size();
    }

    private static void parseRevisionIndex(String suffix, Set<Integer> target) {
        try {
            int index = Integer.parseInt(suffix);
            if (index > 0) target.add(index);
        } catch (NumberFormatException ignored) {
        }
    }

    private List<PrintQuoteResponse> listQuotesForRequest(PrintRequest request) {
        Integer requestId = request.getPrintRequestId();
        int revisionsUsed = countPostQuoteRevisions(requestId);
        return quotes.findByPrintRequestIdOrderByVersionNumberDesc(requestId).stream()
                .map(quote -> toResponse(
                        request,
                        quote,
                        quoteItems.findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(quote.getPrintQuoteId()),
                        revisionsUsed))
                .toList();
    }

    private void expireAndCancelLocked(PrintRequest request, List<PrintQuote> existingQuotes, Instant now) {
        Integer requestId = request.getPrintRequestId();
        for (PrintQuote quote : existingQuotes) {
            if ("ISSUED".equals(quote.getQuoteStatus())) {
                quote.expire();
                quotes.saveAndFlush(quote);
            }
        }
        String fromStatus = request.getStatus();
        request.cancelExpiredQuote(QUOTE_EXPIRED_REASON, now);
        requests.saveAndFlush(request);
        if (!"CANCELLED".equals(fromStatus)) {
            histories.saveAndFlush(new PrintRequestStatusHistory(
                    requestId, fromStatus, "CANCELLED", null,
                    QUOTE_EXPIRED_REASON, "POD_QUOTE_EXPIRED:" + requestId));
        }
        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("expiredAt", now.toString());
        auditData.put("firstQuoteExpiresAt", canonicalDeadline(existingQuotes.getFirst()).toString());
        audits.saveAndFlush(new AuditLog(null, "POD_QUOTE_EXPIRED", "PRINT_REQUEST",
                requestId, QUOTE_EXPIRED_REASON, json.writeValueAsString(auditData)));
    }

    private Instant canonicalDeadline(PrintQuote firstQuote) {
        if (firstQuote.getIssuedAt() == null || firstQuote.getExpiresAt() == null) {
            throw conflict("First quote deadline metadata is incomplete.");
        }
        Instant deadline = firstQuote.getIssuedAt().plus(QUOTE_VALIDITY_WINDOW);
        if (!deadline.equals(firstQuote.getExpiresAt())) {
            throw conflict("First quote deadline metadata is inconsistent.");
        }
        return deadline;
    }

    private PrintQuoteResponse toResponse(PrintRequest request, PrintQuote quote,
            List<PrintQuoteItem> items, int revisionsUsed) {
        boolean current = quote.getPrintQuoteId() != null
                && quote.getPrintQuoteId().equals(request.getCurrentQuoteId());
        int remaining = Math.max(0,
                PrintRequestReviewPolicy.MAX_POST_QUOTE_REVISIONS - revisionsUsed);
        List<PrintQuoteItemResponse> itemResponses = items.stream()
                .map(item -> new PrintQuoteItemResponse(
                        item.getPrintQuoteItemId(),
                        item.getPrintRequestItemId(),
                        item.getFileVersionSnapshot(),
                        item.getSpecificationVersionSnapshot(),
                        json.readTree(item.getSpecificationSnapshot()),
                        item.getQuantitySnapshot(),
                        item.getMaterialCostSnapshot(),
                        item.getLaborCostSnapshot(),
                        item.getOptionFeeSnapshot(),
                        item.getLineTotal()))
                .toList();
        return new PrintQuoteResponse(
                quote.getPrintQuoteId(),
                quote.getPrintRequestId(),
                quote.getVersionNumber(),
                quote.getFileVersionSnapshot(),
                quote.getSpecificationVersionSnapshot(),
                quote.getTotalAmount(),
                quote.getDepositAmount(),
                quote.getRemainingAmount(),
                quote.getEtaAt(),
                quote.getIssuedAt(),
                quote.getExpiresAt(),
                quote.getAcceptedAt(),
                quote.getQuoteStatus(),
                current,
                revisionsUsed,
                remaining,
                itemResponses);
    }

    private Map<Integer, JsonNode> extractSpecItemsById(String specificationData) {
        Map<Integer, JsonNode> map = new LinkedHashMap<>();
        JsonNode root = json.readTree(specificationData);
        JsonNode arr = root == null ? null : root.path("items");
        if (arr != null && arr.isArray()) {
            for (JsonNode node : arr) {
                if (node.isObject() && node.path("printRequestItemId").isInt()) {
                    map.put(node.path("printRequestItemId").asInt(), node);
                }
            }
        }
        return map;
    }

    private String buildItemSpecificationSnapshot(PrintRequestItem item, JsonNode fromSpecVersion) {
        if (fromSpecVersion != null && fromSpecVersion.isObject()) {
            return json.writeValueAsString(fromSpecVersion);
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("printRequestItemId", item.getPrintRequestItemId());
        fallback.put("materialId", item.getMaterialId());
        fallback.put("color", item.getColor());
        fallback.put("quality", item.getQuality());
        fallback.put("infillPercent", item.getInfillPercent());
        fallback.put("widthMm", item.getWidthMm());
        fallback.put("heightMm", item.getHeightMm());
        fallback.put("depthMm", item.getDepthMm());
        fallback.put("quantity", item.getQuantity());
        fallback.put("notes", item.getNotes());
        return json.writeValueAsString(fallback);
    }

    private static void validateQuoteInput(CreatePrintQuoteRequest input) {
        if (input == null || input.expectedFileVersion() == null
                || input.expectedFileVersion() <= 0 || input.expectedSpecVersion() == null
                || input.expectedSpecVersion() <= 0) {
            throw ApiException.invalidRequest("Expected file and specification versions are required.");
        }
        if (input.items() == null || input.items().isEmpty()
                || input.items().stream().anyMatch(item -> item == null
                        || item.printRequestItemId() == null || item.printRequestItemId() <= 0)) {
            throw ApiException.invalidRequest("Valid quote items are required.");
        }
    }

    private static void validateQuoteId(Integer quoteId) {
        if (quoteId == null || quoteId <= 0) {
            throw ApiException.invalidRequest("printQuoteId must be a positive integer.");
        }
    }

    private static BigDecimal normalizeNonNegativeAmount(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.scale() > 2) {
            throw ApiException.invalidRequest(fieldName + " must be a non-negative amount with at most 2 decimals.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private record PreparedQuoteLine(
            Integer printRequestItemId,
            Integer quantitySnapshot,
            String specificationSnapshot,
            BigDecimal materialCost,
            BigDecimal laborCost,
            BigDecimal optionFee,
            BigDecimal lineTotal
    ) {}
}
