package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.response.PagedStaffProductionBoardResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintProductionItemTransitionResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintProductionSummaryResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffProductionBoardItemResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffProductionDetailResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffProductionItemResponse;
import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.repository.PrintQuoteItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestProductionManagementService {
    private static final Set<String> BOARD_FILTERS = Set.of("ALL", "QUEUED", "PRINTING", "COMPLETED");
    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository items;
    private final PrintQuoteRepository quotes;
    private final PrintQuoteItemRepository quoteItems;
    private final PrintRequestStatusHistoryRepository histories;
    private final AuditLogRepository audits;
    private final ObjectMapper json;

    public PagedStaffProductionBoardResponse board(String itemStatus, int page, int size) {
        PrintRequestValidation.validatePage(page, size);
        if (itemStatus == null || !BOARD_FILTERS.contains(itemStatus)) {
            throw ApiException.invalidRequest("itemStatus must be ALL, QUEUED, PRINTING or COMPLETED.");
        }
        Page<PrintRequest> result = requests.findProductionBoard(itemStatus,
                PageRequest.of(page, size, Sort.by("lockedAt").ascending()
                        .and(Sort.by("printRequestId").ascending())));
        List<Integer> ids = result.getContent().stream().map(PrintRequest::getPrintRequestId).toList();
        Map<Integer, List<PrintRequestItem>> byRequest = ids.isEmpty() ? Map.of()
                : items.findByPrintRequestIdInOrderByPrintRequestIdAscPrintRequestItemIdAsc(ids)
                        .stream().collect(Collectors.groupingBy(PrintRequestItem::getPrintRequestId));
        return new PagedStaffProductionBoardResponse(result.getContent().stream()
                .map(request -> boardItem(request, byRequest.getOrDefault(request.getPrintRequestId(), List.of())))
                .toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    public StaffProductionDetailResponse detail(Integer requestId) {
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        PrintQuote quote = eligible(request);
        List<PrintRequestItem> requestItems = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (requestItems.isEmpty()) throw conflict("Print Request has no production items.");
        var quoteRows = quoteItems.findByPrintQuoteIdOrderByPrintQuoteItemIdAsc(quote.getPrintQuoteId());
        Set<Integer> itemIds = requestItems.stream().map(PrintRequestItem::getPrintRequestItemId)
                .collect(Collectors.toSet());
        Set<Integer> quoteItemIds = quoteRows.stream().map(row -> row.getPrintRequestItemId())
                .collect(Collectors.toSet());
        if (quoteRows.size() != requestItems.size()
                || !quoteItemIds.equals(itemIds)
                || quoteRows.stream().anyMatch(row -> !itemIds.contains(row.getPrintRequestItemId())
                        || !quote.getFileVersionSnapshot().equals(row.getFileVersionSnapshot())
                        || !quote.getSpecificationVersionSnapshot().equals(row.getSpecificationVersionSnapshot()))) {
            throw conflict("Accepted Quote item snapshots are inconsistent.");
        }
        return new StaffProductionDetailResponse(requestId, request.getStatus(), request.getLockedAt(),
                request.getHandledByUserId(), quote.getPrintQuoteId(), request.getCurrentFileVersion(),
                request.getCurrentSpecVersion(), summary(requestItems),
                requestItems.stream().map(item -> new StaffProductionItemResponse(itemResponse(item),
                        "QUEUED".equals(item.getProductionStatus()),
                        "PRINTING".equals(item.getProductionStatus()))).toList(),
                quoteRows.stream().map(row -> new PrintQuoteItemResponse(row.getPrintQuoteItemId(),
                        row.getPrintRequestItemId(), row.getFileVersionSnapshot(),
                        row.getSpecificationVersionSnapshot(), json.readTree(row.getSpecificationSnapshot()),
                        row.getQuantitySnapshot(), row.getMaterialCostSnapshot(),
                        row.getLaborCostSnapshot(), row.getOptionFeeSnapshot(), row.getLineTotal())).toList());
    }

    @Transactional
    public PrintProductionItemTransitionResponse start(Integer staffId, Integer requestId, Integer itemId) {
        return transition(staffId, requestId, itemId, "QUEUED", "PRINTING");
    }

    @Transactional
    public PrintProductionItemTransitionResponse complete(Integer staffId, Integer requestId, Integer itemId) {
        return transition(staffId, requestId, itemId, "PRINTING", "COMPLETED");
    }

    private PrintProductionItemTransitionResponse transition(Integer staffId, Integer requestId,
            Integer itemId, String expected, String target) {
        PrintRequestValidation.validateCustomerId(staffId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (itemId == null || itemId <= 0) throw ApiException.invalidRequest("Item ID must be positive.");
        PrintRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        eligible(request);
        List<PrintRequestItem> requestItems = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        PrintRequestItem item = requestItems.stream()
                .filter(value -> itemId.equals(value.getPrintRequestItemId())
                        && requestId.equals(value.getPrintRequestId()))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Production item was not found in this Print Request."));
        if (target.equals(item.getProductionStatus())) {
            return result(requestId, item, requestItems);
        }
        if (!expected.equals(item.getProductionStatus())) {
            throw conflict("Production item cannot transition from its current state.");
        }
        if ("PRINTING".equals(target)) item.startPrinting();
        else item.completePrinting();
        items.saveAndFlush(item);
        Instant now = Instant.now();
        audits.saveAndFlush(new AuditLog(staffId,
                "PRINTING".equals(target) ? "POD_ITEM_PRINTING_STARTED" : "POD_ITEM_PRINTING_COMPLETED",
                "PRINT_REQUEST_ITEM", itemId, null,
                json.writeValueAsString(Map.of("printRequestId", requestId,
                        "printRequestItemId", itemId, "fromStatus", expected,
                        "toStatus", target, "changedAt", now.toString()))));
        return result(requestId, item, requestItems);
    }

    private PrintQuote eligible(PrintRequest request) {
        if (!"IN_QUEUE".equals(request.getStatus()) || request.getLockedAt() == null
                || request.getCancelledAt() != null || request.getCurrentQuoteId() == null
                || !histories.existsByPrintRequestIdAndToStatusAndSourceEventIdStartingWith(
                        request.getPrintRequestId(), "IN_QUEUE",
                        "POD_PAYMENT_VERIFIED:" + request.getPrintRequestId() + ":tx:")) {
            throw conflict("Print Request is not in the verified production queue.");
        }
        PrintQuote quote = quotes.findByPrintQuoteIdAndPrintRequestId(
                request.getCurrentQuoteId(), request.getPrintRequestId())
                .orElseThrow(() -> conflict("Current accepted Quote is unavailable."));
        if (!"ACCEPTED".equals(quote.getQuoteStatus())
                || quote.getFileVersionSnapshot() == null
                || !quote.getFileVersionSnapshot().equals(request.getCurrentFileVersion())
                || quote.getSpecificationVersionSnapshot() == null
                || !quote.getSpecificationVersionSnapshot().equals(request.getCurrentSpecVersion())) {
            throw conflict("Current accepted Quote does not match locked production versions.");
        }
        return quote;
    }

    private static StaffProductionBoardItemResponse boardItem(PrintRequest request,
            List<PrintRequestItem> requestItems) {
        return new StaffProductionBoardItemResponse(request.getPrintRequestId(), request.getStatus(),
                request.getLockedAt(), request.getHandledByUserId(), request.getCurrentQuoteId(),
                request.getCurrentFileVersion(), request.getCurrentSpecVersion(), summary(requestItems));
    }

    private static PrintProductionItemTransitionResponse result(Integer requestId,
            PrintRequestItem item, List<PrintRequestItem> requestItems) {
        return new PrintProductionItemTransitionResponse(requestId, item.getPrintRequestItemId(),
                item.getProductionStatus(), summary(requestItems));
    }

    private static PrintProductionSummaryResponse summary(List<PrintRequestItem> requestItems) {
        return PrintProductionSummaryResponse.fromStatuses(
                requestItems.stream().map(PrintRequestItem::getProductionStatus).toList());
    }

    private static PrintRequestItemResponse itemResponse(PrintRequestItem item) {
        return new PrintRequestItemResponse(item.getPrintRequestItemId(), item.getMaterialId(),
                item.getModelVersionId(), item.getModelVersionId() == null ? "UPLOADED_FILE" : "MODEL_VERSION",
                item.getColor(), item.getQuality(), item.getInfillPercent(), item.getWidthMm(),
                item.getHeightMm(), item.getDepthMm(), item.getQuantity(), item.getNotes(),
                item.getProductionStatus());
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
