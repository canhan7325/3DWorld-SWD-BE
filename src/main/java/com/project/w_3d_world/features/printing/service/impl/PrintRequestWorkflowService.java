package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.features.printing.dto.response.PrintRequestWorkflowResponse;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.repository.PrintQuoteRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestWorkflowPolicy;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestWorkflowService {
    private final PrintRequestRepository requests;
    private final PrintQuoteRepository quotes;
    private final PrintRequestStatusHistoryRepository histories;
    private final PrintRequestReviewPolicy reviewPolicy;
    private final PrintRequestWorkflowPolicy workflowPolicy;

    public PrintRequestWorkflowResponse forCustomer(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        var request = requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        var quoteRows = quotes.findByPrintRequestIdOrderByVersionNumberAsc(requestId);
        String prefix = "POD_POST_QUOTE_REVISION:" + requestId + ":";
        int completed = (int) histories
                .findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(requestId)
                .stream().map(row -> row.getSourceEventId())
                .filter(event -> event != null && event.startsWith(prefix))
                .distinct().count();
        boolean pending = workflowPolicy.isCorrection(request)
                && reviewPolicy.latest(requestId)
                        .filter(decision -> "CORRECTION_REQUESTED".equals(decision.outcome()))
                        .filter(decision -> decision.quoteDeadline() == null
                                || Instant.now().isBefore(decision.quoteDeadline()))
                        .isPresent();
        return workflowPolicy.describe(request, quoteRows, completed, pending, Instant.now());
    }
}
