package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.request.ApprovePrintRequestReviewRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintCorrectionRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestTechnicalReviewResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.entity.PrintRequestTechnicalReview;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestTechnicalReviewRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestReviewPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestSubmissionValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PrintRequestTechnicalReviewService {
    private final PrintRequestRepository requests;
    private final PrintRequestFileVersionRepository files;
    private final PrintRequestSpecVersionRepository specs;
    private final PrintRequestTechnicalReviewRepository reviews;
    private final PrintRequestStatusHistoryRepository histories;
    private final AuditLogRepository audits;
    private final PrintRequestReviewPolicy reviewPolicy;
    private final PrintRequestItemRepository items;
    private final PrintRequestSubmissionValidation submissionValidation;
    private final ObjectMapper json;

    @Transactional
    public PrintRequestTechnicalReviewResponse requestCorrection(Integer reviewerId,
            Integer requestId, RequestPrintCorrectionRequest input) {
        validateActorAndPair(reviewerId, requestId,
                input == null ? null : input.expectedFileVersion(),
                input == null ? null : input.expectedSpecVersion());
        if (input.reason() == null || input.reason().isBlank()
                || input.reason().trim().length() > 10_000
                || input.reason().chars().anyMatch(Character::isISOControl)) {
            throw ApiException.invalidRequest("A meaningful correction reason is required.");
        }
        PrintRequest request = reviewable(requestId, input.expectedFileVersion(),
                input.expectedSpecVersion());
        if (reviewPolicy.latest(requestId).filter(decision ->
                decision.fileVersion() == input.expectedFileVersion()
                        && decision.specVersion() == input.expectedSpecVersion()).isPresent()) {
            throw conflict("Current Print Request versions have already been reviewed.");
        }
        String reason = input.reason().trim();
        var saved = saveReview(requestId, reviewerId, "CORRECTION_REQUESTED",
                input.expectedFileVersion(), input.expectedSpecVersion(), reason);
        request.requireRevision();
        requests.saveAndFlush(request);
        histories.saveAndFlush(new PrintRequestStatusHistory(requestId, "SUBMITTED",
                "REQUIRES_REVISION", reviewerId, reason, null));
        audit(reviewerId, requestId, "POD_CORRECTION_REQUESTED",
                input.expectedFileVersion(), input.expectedSpecVersion(), reason);
        return response(saved, "CORRECTION_REQUESTED", input.expectedFileVersion(),
                input.expectedSpecVersion(), reason);
    }

    @Transactional
    public PrintRequestTechnicalReviewResponse approve(Integer reviewerId,
            Integer requestId, ApprovePrintRequestReviewRequest input) {
        validateActorAndPair(reviewerId, requestId,
                input == null ? null : input.expectedFileVersion(),
                input == null ? null : input.expectedSpecVersion());
        PrintRequest request = reviewable(requestId, input.expectedFileVersion(),
                input.expectedSpecVersion());
        if (reviewPolicy.latest(requestId).filter(decision ->
                decision.fileVersion() == input.expectedFileVersion()
                        && decision.specVersion() == input.expectedSpecVersion()).isPresent()) {
            throw conflict("Current Print Request versions have already been reviewed.");
        }
        submissionValidation.validate(request,
                files.findByPrintRequestIdAndVersion(requestId, input.expectedFileVersion()).orElseThrow(),
                items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId));
        var saved = saveReview(requestId, reviewerId, "APPROVED",
                input.expectedFileVersion(), input.expectedSpecVersion(), null);
        audit(reviewerId, requestId, "POD_REVIEW_APPROVED",
                input.expectedFileVersion(), input.expectedSpecVersion(), null);
        return response(saved, "APPROVED", input.expectedFileVersion(),
                input.expectedSpecVersion(), null);
    }

    private PrintRequest reviewable(Integer requestId, Integer fileVersion, Integer specVersion) {
        PrintRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        if (!"SUBMITTED".equals(request.getStatus()) || request.getSubmittedAt() == null
                || request.getCancelledAt() != null || request.getLockedAt() != null
                || request.getCurrentQuoteId() != null) {
            throw conflict("Print Request is not available for review.");
        }
        if (!fileVersion.equals(request.getCurrentFileVersion())
                || !specVersion.equals(request.getCurrentSpecVersion())) {
            throw conflict("Print Request versions have changed.");
        }
        if (files.findByPrintRequestIdAndVersion(requestId, fileVersion).isEmpty()
                || specs.findByPrintRequestIdAndVersion(requestId, specVersion).isEmpty()) {
            throw conflict("Current Print Request versions are unavailable.");
        }
        return request;
    }

    private PrintRequestTechnicalReview saveReview(Integer requestId, Integer reviewerId,
            String outcome, int fileVersion, int specVersion, String reason) {
        var data = new LinkedHashMap<String, Object>();
        data.put("outcome", outcome);
        data.put("reviewedFileVersion", fileVersion);
        data.put("reviewedSpecVersion", specVersion);
        if (reason != null) data.put("reason", reason);
        return reviews.saveAndFlush(new PrintRequestTechnicalReview(requestId, reviewerId,
                json.writeValueAsString(data)));
    }

    private void audit(Integer reviewerId, Integer requestId, String action,
            int fileVersion, int specVersion, String reason) {
        var data = new LinkedHashMap<String, Object>();
        data.put("reviewedFileVersion", fileVersion);
        data.put("reviewedSpecVersion", specVersion);
        audits.saveAndFlush(new AuditLog(reviewerId, action, "PRINT_REQUEST", requestId,
                reason, json.writeValueAsString(data)));
    }

    private static PrintRequestTechnicalReviewResponse response(PrintRequestTechnicalReview saved,
            String outcome, int fileVersion, int specVersion, String reason) {
        return new PrintRequestTechnicalReviewResponse(saved.getTechnicalReviewId(), outcome,
                fileVersion, specVersion, reason, saved.getReviewerUserId(), saved.getCreatedAt());
    }

    private static void validateActorAndPair(Integer reviewerId, Integer requestId,
            Integer fileVersion, Integer specVersion) {
        PrintRequestValidation.validateCustomerId(reviewerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (fileVersion == null || fileVersion <= 0 || specVersion == null || specVersion <= 0) {
            throw ApiException.invalidRequest("Expected file and specification versions are required.");
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
