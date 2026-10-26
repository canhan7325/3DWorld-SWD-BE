package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestShippingReadinessResponse;
import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestShippingReadinessService {
    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository items;
    private final PrintQuoteRepository quotes;
    private final PrintRequestStatusHistoryRepository histories;
    private final EntityManager entityManager;
    private final ObjectMapper json;

    public PrintRequestShippingReadinessResponse forCustomer(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        return evaluate(request);
    }

    public PrintRequestShippingReadinessResponse forStaff(Integer requestId) {
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Print Request was not found."));
        return evaluate(request);
    }

    private PrintRequestShippingReadinessResponse evaluate(PrintRequest request) {
        Integer requestId = request.getPrintRequestId();
        List<String> blockers = new ArrayList<>();
        if (request.getCustomerUserId() == null || request.getCustomerUserId() <= 0) {
            blockers.add("CUSTOMER_INVALID");
        }
        if (!"IN_QUEUE".equals(request.getStatus()) || request.getLockedAt() == null
                || request.getCancelledAt() != null) {
            blockers.add("NOT_IN_PRODUCTION_QUEUE");
        }
        if (!histories.existsByPrintRequestIdAndToStatusAndSourceEventIdStartingWith(requestId,
                "IN_QUEUE", "POD_PAYMENT_VERIFIED:" + requestId + ":tx:")) {
            blockers.add("VERIFIED_PAYMENT_QUEUE_EVENT_MISSING");
        }
        PrintQuote quote = request.getCurrentQuoteId() == null ? null
                : quotes.findByPrintQuoteIdAndPrintRequestId(request.getCurrentQuoteId(), requestId)
                        .orElse(null);
        if (quote == null || !"ACCEPTED".equals(quote.getQuoteStatus())
                || quote.getAcceptedAt() == null
                || request.getCurrentFileVersion() == null
                || !request.getCurrentFileVersion().equals(quote.getFileVersionSnapshot())
                || request.getCurrentSpecVersion() == null
                || !request.getCurrentSpecVersion().equals(quote.getSpecificationVersionSnapshot())
                || quote.getTotalAmount() == null || quote.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0
                || quote.getDepositAmount() == null
                || quote.getDepositAmount().compareTo(quote.getTotalAmount()) != 0
                || quote.getRemainingAmount() == null
                || quote.getRemainingAmount().compareTo(BigDecimal.ZERO) != 0) {
            blockers.add("CURRENT_ACCEPTED_FULL_QUOTE_MISSING");
        }
        List<PrintRequestItem> requestItems = items
                .findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        int completed = (int) requestItems.stream()
                .filter(item -> "COMPLETED".equals(item.getProductionStatus())).count();
        if (requestItems.isEmpty()) blockers.add("PRODUCTION_ITEMS_MISSING");
        else if (requestItems.stream().anyMatch(item -> item.getQuantity() == null
                || item.getQuantity() <= 0 || !"COMPLETED".equals(item.getProductionStatus()))) {
            blockers.add("PRODUCTION_ITEMS_INCOMPLETE");
        }
        if (request.getShippingAddressId() == null || !validAddressSnapshot(request.getShippingAddressSnapshot())) {
            blockers.add("SHIPPING_ADDRESS_MISSING");
        }
        boolean shipmentExists = entityManager.createQuery(
                        "select count(s) from Shipment s where s.printRequestId = :requestId", Long.class)
                .setParameter("requestId", requestId).getSingleResult() > 0;
        if (shipmentExists) blockers.add("POD_SHIPMENT_ALREADY_EXISTS");
        return new PrintRequestShippingReadinessResponse(blockers.isEmpty(),
                List.copyOf(blockers), completed, requestItems.size(), shipmentExists);
    }

    private boolean validAddressSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return false;
        try {
            var value = json.readTree(snapshot);
            return value.isObject() && nonBlank(value.path("recipientName").asText())
                    && nonBlank(value.path("phone").asText())
                    && nonBlank(value.path("addressLine").asText())
                    && nonBlank(value.path("province").asText())
                    && nonBlank(value.path("district").asText())
                    && nonBlank(value.path("ward").asText());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
