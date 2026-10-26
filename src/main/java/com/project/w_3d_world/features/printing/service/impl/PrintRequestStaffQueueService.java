package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.response.PagedStaffReviewQueueResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffReviewQueueItemResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestTechnicalReview;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestTechnicalReviewRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
public class PrintRequestStaffQueueService {
    private static final Set<String> ASSIGNMENTS = Set.of("ALL", "UNASSIGNED", "ASSIGNED", "MINE");

    private final PrintRequestRepository requests;
    private final PrintRequestFileVersionRepository files;
    private final PrintRequestSpecVersionRepository specs;
    private final PrintRequestTechnicalReviewRepository reviews;
    private final AuditLogRepository audits;
    private final ObjectMapper json;

    public PagedStaffReviewQueueResponse list(Integer staffId, String assignment, int page, int size) {
        PrintRequestValidation.validateCustomerId(staffId);
        PrintRequestValidation.validatePage(page, size);
        if (assignment == null || !ASSIGNMENTS.contains(assignment)) {
            throw ApiException.invalidRequest("assignment must be ALL, UNASSIGNED, ASSIGNED or MINE.");
        }
        Page<PrintRequest> result = requests.findStaffReviewQueue(assignment, staffId,
                PageRequest.of(page, size, Sort.by("submittedAt").ascending()
                        .and(Sort.by("printRequestId").ascending())));
        List<Integer> ids = result.getContent().stream().map(PrintRequest::getPrintRequestId).toList();
        Map<Integer, PrintRequestTechnicalReview> latest = ids.isEmpty() ? Map.of()
                : reviews.findByPrintRequestIdInOrderByCreatedAtDescTechnicalReviewIdDesc(ids)
                        .stream().collect(Collectors.toMap(PrintRequestTechnicalReview::getPrintRequestId,
                                Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        return new PagedStaffReviewQueueResponse(result.getContent().stream()
                .map(request -> response(request, staffId, latest.get(request.getPrintRequestId())))
                .toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public StaffReviewQueueItemResponse claim(Integer staffId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(staffId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        if (!reviewable(request)
                || files.findByPrintRequestIdAndVersion(requestId, request.getCurrentFileVersion()).isEmpty()
                || specs.findByPrintRequestIdAndVersion(requestId, request.getCurrentSpecVersion()).isEmpty()) {
            throw conflict("Print Request is not available for Staff review assignment.");
        }
        Integer assigned = request.getHandledByUserId();
        if (assigned != null && !assigned.equals(staffId)) {
            throw conflict("Print Request is already assigned to another Staff member.");
        }
        if (assigned == null) {
            Instant now = Instant.now();
            request.claimReview(staffId, now);
            requests.saveAndFlush(request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("assignedStaffId", staffId);
            data.put("currentFileVersion", request.getCurrentFileVersion());
            data.put("currentSpecVersion", request.getCurrentSpecVersion());
            data.put("handledAt", now.toString());
            audits.saveAndFlush(new AuditLog(staffId, "POD_STAFF_REVIEW_CLAIMED",
                    "PRINT_REQUEST", requestId, null, json.writeValueAsString(data)));
        }
        PrintRequestTechnicalReview latest = reviews
                .findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(requestId)
                .stream().findFirst().orElse(null);
        return response(request, staffId, latest);
    }

    private StaffReviewQueueItemResponse response(PrintRequest request, Integer staffId,
            PrintRequestTechnicalReview latest) {
        boolean reviewedCurrentPair = false;
        boolean approved = false;
        if (latest != null) {
            var data = json.readTree(latest.getReviewData());
            if (!data.isObject() || !data.path("reviewedFileVersion").isInt()
                    || !data.path("reviewedSpecVersion").isInt()
                    || !("APPROVED".equals(data.path("outcome").asText())
                            || "CORRECTION_REQUESTED".equals(data.path("outcome").asText()))) {
                throw conflict("Print Request review data is invalid.");
            }
            reviewedCurrentPair = request.getCurrentFileVersion().equals(data.path("reviewedFileVersion").asInt())
                    && request.getCurrentSpecVersion().equals(data.path("reviewedSpecVersion").asInt());
            approved = reviewedCurrentPair && "APPROVED".equals(data.path("outcome").asText());
        }
        return new StaffReviewQueueItemResponse(request.getPrintRequestId(),
                request.getSubmittedAt(), request.getStatus(), request.getHandledByUserId(),
                request.getHandledAt(), request.getCurrentFileVersion(), request.getCurrentSpecVersion(),
                !reviewedCurrentPair, approved,
                request.getHandledByUserId() == null || request.getHandledByUserId().equals(staffId));
    }

    private static boolean reviewable(PrintRequest request) {
        return "SUBMITTED".equals(request.getStatus()) && request.getSubmittedAt() != null
                && request.getCancelledAt() == null && request.getLockedAt() == null
                && request.getCurrentQuoteId() == null && request.getCurrentFileVersion() != null
                && request.getCurrentSpecVersion() != null;
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
