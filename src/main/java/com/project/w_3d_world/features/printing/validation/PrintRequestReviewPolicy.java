package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.exception.QuoteExpiredException;
import com.project.w_3d_world.features.printing.repository.PrintRequestTechnicalReviewRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PrintRequestReviewPolicy {
    public static final int MAX_POST_QUOTE_REVISIONS = 3;

    public enum Readiness {
        CURRENT_APPROVED, FILE_CHANGED, SPEC_CHANGED, CORRECTION_PENDING,
        LOCKED_OR_CANCELLED, NO_APPROVAL
    }

    public record Decision(String outcome, int fileVersion, int specVersion,
            Integer revisionNumber, Instant quoteDeadline) {
        public Decision(String outcome, int fileVersion, int specVersion) {
            this(outcome, fileVersion, specVersion, null, null);
        }
    }

    private final PrintRequestTechnicalReviewRepository reviews;
    private final ObjectMapper json;

    public Optional<Decision> latest(Integer requestId) {
        return reviews.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(requestId)
                .stream().findFirst().map(review -> {
                    var data = json.readTree(review.getReviewData());
                    if (!data.isObject() || !data.path("reviewedFileVersion").isInt()
                            || !data.path("reviewedSpecVersion").isInt()) {
                        throw conflict("Print Request review data is invalid.");
                    }
                    String outcome = data.path("outcome").asText();
                    int file = data.path("reviewedFileVersion").asInt();
                    int spec = data.path("reviewedSpecVersion").asInt();
                    if (!("APPROVED".equals(outcome) || "CORRECTION_REQUESTED".equals(outcome))
                            || file <= 0 || spec <= 0) {
                        throw conflict("Print Request review data is invalid.");
                    }
                    Integer revisionNumber = null;
                    if (!data.path("revisionNumber").isMissingNode()
                            && !data.path("revisionNumber").isNull()) {
                        if (!data.path("revisionNumber").isInt()) {
                            throw conflict("Print Request review data is invalid.");
                        }
                        revisionNumber = data.path("revisionNumber").asInt();
                        if (revisionNumber <= 0) {
                            throw conflict("Print Request review data is invalid.");
                        }
                    }
                    Instant quoteDeadline = null;
                    if (!data.path("quoteDeadline").isMissingNode()
                            && !data.path("quoteDeadline").isNull()) {
                        if (!data.path("quoteDeadline").isTextual()) {
                            throw conflict("Print Request review data is invalid.");
                        }
                        try {
                            quoteDeadline = Instant.parse(data.path("quoteDeadline").asText());
                        } catch (DateTimeParseException exception) {
                            throw conflict("Print Request review data is invalid.");
                        }
                    }
                    return new Decision(outcome, file, spec, revisionNumber, quoteDeadline);
                });
    }

    public Readiness readiness(PrintRequest request) {
        if (request.getCancelledAt() != null || request.getLockedAt() != null
                || request.getCurrentQuoteId() != null) return Readiness.LOCKED_OR_CANCELLED;
        Optional<Decision> latest = latest(request.getPrintRequestId());
        if (latest.isEmpty()) return Readiness.NO_APPROVAL;
        Decision decision = latest.get();
        if ("CORRECTION_REQUESTED".equals(decision.outcome())) return Readiness.CORRECTION_PENDING;
        if (!Integer.valueOf(decision.fileVersion()).equals(request.getCurrentFileVersion())) {
            return Readiness.FILE_CHANGED;
        }
        if (!Integer.valueOf(decision.specVersion()).equals(request.getCurrentSpecVersion())) {
            return Readiness.SPEC_CHANGED;
        }
        return "SUBMITTED".equals(request.getStatus())
                ? Readiness.CURRENT_APPROVED : Readiness.NO_APPROVAL;
    }

    public void requireCurrentApproved(PrintRequest request) {
        if (readiness(request) != Readiness.CURRENT_APPROVED) {
            throw conflict("Current Print Request versions have no valid Staff approval.");
        }
    }

    public Decision requirePendingCorrection(PrintRequest request) {
        if (!"REQUIRES_REVISION".equals(request.getStatus())
                || request.getLockedAt() != null || request.getCurrentQuoteId() != null
                || request.getCancelledAt() != null) {
            throw conflict("Print Request has no active correction authorization.");
        }
        Decision decision = latest(request.getPrintRequestId())
                .orElseThrow(() -> conflict("Print Request has no active correction authorization."));
        if (!"CORRECTION_REQUESTED".equals(decision.outcome())) {
            throw conflict("Print Request has no active correction authorization.");
        }
        if (decision.revisionNumber() != null
                && decision.revisionNumber() > MAX_POST_QUOTE_REVISIONS) {
            throw conflict("Buyer revision limit of 3 post-quote revisions has been reached.");
        }
        if (decision.quoteDeadline() != null
                && !Instant.now().isBefore(decision.quoteDeadline())) {
            throw new QuoteExpiredException("Print Request quote 24-hour deadline has expired.");
        }
        return decision;
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
