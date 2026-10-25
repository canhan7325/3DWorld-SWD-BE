package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.features.printing.entity.PrintQuote;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestWorkflowResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PrintRequestWorkflowPolicy {
    public static boolean isDraft(PrintRequest request) {
        return "DRAFT".equals(request.getStatus()) && request.getSubmittedAt() == null;
    }

    public static boolean isCorrection(PrintRequest request) {
        return "REQUIRES_REVISION".equals(request.getStatus());
    }

    public static boolean isUnlocked(PrintRequest request) {
        return request.getLockedAt() == null && request.getCancelledAt() == null
                && !"CANCELLED".equals(request.getStatus())
                && !"IN_QUEUE".equals(request.getStatus());
    }

    public static boolean mayEdit(PrintRequest request) {
        return isUnlocked(request) && request.getCurrentQuoteId() == null
                && (isDraft(request) || isCorrection(request));
    }

    public static boolean mayCancel(PrintRequest request) {
        return isUnlocked(request) && request.getCurrentQuoteId() == null
                && request.getHandledAt() == null && request.getHandledByUserId() == null
                && (isDraft(request) || "SUBMITTED".equals(request.getStatus())
                        || isCorrection(request));
    }

    public static boolean beforeQuoteDeadline(Instant now, Instant deadline) {
        return deadline != null && now.isBefore(deadline);
    }

    public PrintRequestWorkflowResponse describe(PrintRequest request, List<PrintQuote> quotes,
            int completedRevisions, boolean pendingCorrection, Instant now) {
        PrintQuote first = quotes.isEmpty() ? null : quotes.getFirst();
        Instant deadline = first == null || first.getIssuedAt() == null ? null
                : first.getIssuedAt().plusSeconds(24 * 60 * 60);
        if (first != null && !deadlineEquals(first, deadline)) deadline = null;
        Integer remaining = first == null ? null
                : Math.max(0, PrintRequestReviewPolicy.MAX_POST_QUOTE_REVISIONS - completedRevisions);
        PrintQuote current = quotes.stream().filter(quote -> quote.getPrintQuoteId() != null
                && quote.getPrintQuoteId().equals(request.getCurrentQuoteId()))
                .findFirst().orElse(null);
        boolean unlocked = isUnlocked(request);
        boolean windowOpen = beforeQuoteDeadline(now, deadline);
        boolean issued = current != null && "ISSUED".equals(current.getQuoteStatus());
        boolean accepted = current != null && "ACCEPTED".equals(current.getQuoteStatus());
        boolean versionsMatch = current != null
                && current.getFileVersionSnapshot() != null
                && current.getFileVersionSnapshot().equals(request.getCurrentFileVersion())
                && current.getSpecificationVersionSnapshot() != null
                && current.getSpecificationVersionSnapshot().equals(request.getCurrentSpecVersion());
        boolean expiredUnaccepted = first != null && deadline != null
                && !windowOpen && !accepted;
        boolean canEdit = !expiredUnaccepted && mayEdit(request)
                && (!isCorrection(request) || pendingCorrection);
        boolean hasVersions = request.getCurrentFileVersion() != null
                && request.getCurrentSpecVersion() != null;
        boolean canConfirm = canEdit && hasVersions && !request.isPreviewConfirmed();
        boolean canSubmit = canEdit && hasVersions && request.isCopyrightConfirmed()
                && request.isPreviewConfirmed();
        boolean canRevise = unlocked && "QUOTED".equals(request.getStatus())
                && issued && versionsMatch && windowOpen && remaining != null && remaining > 0;
        boolean canAccept = unlocked && "QUOTED".equals(request.getStatus())
                && issued && versionsMatch && windowOpen;
        boolean canPay = unlocked && "QUOTE_ACCEPTED".equals(request.getStatus())
                && accepted && versionsMatch;
        String display = switch (request.getStatus()) {
            case "REQUIRES_REVISION" -> "REQUIRES_REVISION";
            case "QUOTED" -> "WAITING_QUOTE_ACCEPTANCE";
            case "QUOTE_ACCEPTED" -> "PAYMENT_PENDING";
            case "IN_QUEUE" -> "QUEUED";
            default -> request.getStatus();
        };
        if (expiredUnaccepted && isUnlocked(request)
                && ("QUOTED".equals(request.getStatus())
                        || "REQUIRES_REVISION".equals(request.getStatus())
                        || "SUBMITTED".equals(request.getStatus()))) {
            display = "QUOTE_EXPIRED";
        }
        return new PrintRequestWorkflowResponse(display, deadline, null, remaining,
                request.getCurrentFileVersion(), request.getCurrentSpecVersion(),
                new PrintRequestWorkflowResponse.AllowedActions(canEdit, canEdit, canConfirm,
                        canSubmit, canRevise, canAccept, canPay,
                        !expiredUnaccepted && mayCancel(request)));
    }

    private static boolean deadlineEquals(PrintQuote quote, Instant deadline) {
        return deadline != null && deadline.equals(quote.getExpiresAt());
    }
}
