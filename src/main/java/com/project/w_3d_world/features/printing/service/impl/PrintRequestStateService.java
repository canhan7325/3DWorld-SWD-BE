package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.request.ConfirmPrintRequestPreviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CancelPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.SubmitPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStateResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStatusHistoryResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestSubmissionValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestWorkflowPolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrintRequestStateService {
    private final PrintRequestRepository requests;
    private final PrintRequestFileVersionRepository fileVersions;
    private final PrintRequestSpecVersionRepository specVersions;
    private final PrintRequestStatusHistoryRepository histories;
    private final PrintRequestItemRepository items;
    private final PrintRequestSubmissionValidation submissionValidation;
    private final PrintRequestReviewPolicy reviewPolicy;

    @Transactional
    public PrintRequestStateResponse confirmPreview(Integer customerId, Integer requestId,
            ConfirmPrintRequestPreviewRequest input) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (input == null || input.expectedFileVersion() == null
                || input.expectedFileVersion() <= 0 || input.expectedSpecVersion() == null
                || input.expectedSpecVersion() <= 0) {
            throw ApiException.invalidRequest("Expected file and specification versions are required.");
        }
        PrintRequest request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        boolean revision = PrintRequestWorkflowPolicy.isCorrection(request);
        if (!PrintRequestWorkflowPolicy.mayEdit(request)) {
            throw new ApiException(HttpStatus.CONFLICT, "Print Request is not an editable Draft.");
        }
        if (revision) requireChangedCorrection(request);
        if (!input.expectedFileVersion().equals(request.getCurrentFileVersion())
                || !input.expectedSpecVersion().equals(request.getCurrentSpecVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "Print Request versions have changed.");
        }
        if (fileVersions.findByPrintRequestIdAndVersion(requestId, input.expectedFileVersion()).isEmpty()
                || specVersions.findByPrintRequestIdAndVersion(requestId, input.expectedSpecVersion()).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Current Print Request versions are unavailable.");
        }
        request.confirmPreview();
        requests.saveAndFlush(request);
        return state(request);
    }

    @Transactional
    public PrintRequestStateResponse submit(Integer customerId, Integer requestId,
            SubmitPrintRequestRequest input) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (input == null || input.expectedFileVersion() == null
                || input.expectedFileVersion() <= 0 || input.expectedSpecVersion() == null
                || input.expectedSpecVersion() <= 0) {
            throw ApiException.invalidRequest("Expected file and specification versions are required.");
        }
        PrintRequest request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        String fromStatus = request.getStatus();
        boolean draft = PrintRequestWorkflowPolicy.isDraft(request);
        boolean revision = PrintRequestWorkflowPolicy.isCorrection(request);
        if (!PrintRequestWorkflowPolicy.mayEdit(request)
                || (draft && (request.getHandledAt() != null || request.getHandledByUserId() != null))) {
            throw new ApiException(HttpStatus.CONFLICT, "Print Request cannot be submitted in its current state.");
        }
        PrintRequestReviewPolicy.Decision decision = revision ? requireChangedCorrection(request) : null;
        if (!input.expectedFileVersion().equals(request.getCurrentFileVersion())
                || !input.expectedSpecVersion().equals(request.getCurrentSpecVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "Print Request versions have changed.");
        }
        if (!request.isCopyrightConfirmed() || !request.isPreviewConfirmed()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Copyright declaration and preview confirmation are required.");
        }
        var currentFile = fileVersions.findByPrintRequestIdAndVersion(requestId,
                input.expectedFileVersion())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Current Print Request versions are unavailable."));
        if (specVersions.findByPrintRequestIdAndVersion(requestId,
                input.expectedSpecVersion()).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Current Print Request versions are unavailable.");
        }
        submissionValidation.validate(request, currentFile,
                items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId));
        request.submit();
        requests.saveAndFlush(request);
        String sourceEventId = (decision != null && decision.revisionNumber() != null)
                ? "POD_POST_QUOTE_REVISION:" + requestId + ":" + decision.revisionNumber()
                : null;
        histories.saveAndFlush(new PrintRequestStatusHistory(
                requestId, fromStatus, "SUBMITTED", customerId, null, sourceEventId));
        return state(request);
    }

    @Transactional
    public PrintRequestStateResponse cancel(Integer customerId, Integer requestId,
            CancelPrintRequestRequest input) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (input == null || input.reason() == null || input.reason().isBlank()
                || input.reason().length() > 10_000
                || input.reason().chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalidRequest("A valid cancellation reason is required.");
        }
        PrintRequest request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        String fromStatus = request.getStatus();
        if (!PrintRequestWorkflowPolicy.mayCancel(request)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Print Request cannot be cancelled in its current state.");
        }
        String reason = input.reason().trim();
        request.cancel(reason);
        requests.saveAndFlush(request);
        histories.saveAndFlush(new PrintRequestStatusHistory(
                requestId, fromStatus, "CANCELLED", customerId, reason, null));
        return state(request);
    }

    @Transactional(readOnly = true)
    public List<PrintRequestStatusHistoryResponse> history(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        return histories.findByPrintRequestIdOrderByChangedAtAscPrintStatusHistoryIdAsc(requestId)
                .stream().map(value -> new PrintRequestStatusHistoryResponse(
                        value.getPrintStatusHistoryId(), value.getFromStatus(), value.getToStatus(),
                        value.getChangedAt(), value.getChangedByUserId(), value.getReason()))
                .toList();
    }

    private static PrintRequestStateResponse state(PrintRequest request) {
        return new PrintRequestStateResponse(request.getPrintRequestId(), request.getStatus(),
                request.isCopyrightConfirmed(), request.isPreviewConfirmed(),
                request.getCurrentFileVersion(), request.getCurrentSpecVersion(),
                request.getSubmittedAt(), request.getCancelledAt(), request.getUpdatedAt());
    }

    private PrintRequestReviewPolicy.Decision requireChangedCorrection(PrintRequest request) {
        var decision = reviewPolicy.requirePendingCorrection(request);
        if (Integer.valueOf(decision.fileVersion()).equals(request.getCurrentFileVersion())
                && Integer.valueOf(decision.specVersion()).equals(request.getCurrentSpecVersion())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A corrected file or specification version is required.");
        }
        return decision;
    }
}
