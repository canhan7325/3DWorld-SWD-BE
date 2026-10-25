package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.request.ApprovePrintRequestReviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintCorrectionRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestTechnicalReviewResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffPrintRequestResponse;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestQuoteService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffAccessService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestTechnicalReviewService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/print/requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PRINT_STAFF','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Staff Print Requests")
public class StaffPrintRequestController {
    private final PrintRequestStaffAccessService access;
    private final PrintRequestTechnicalReviewService reviews;
    private final PrintRequestQuoteService quotes;

    @GetMapping("/{printRequestId}")
    public ApiResponse<StaffPrintRequestResponse> detail(@PathVariable String printRequestId) {
        return ApiResponse.success(access.detail(
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/{printRequestId}/file/download")
    public void downloadCurrentFile(@PathVariable String printRequestId,
            HttpServletResponse response) throws IOException {
        int requestId = PrintRequestValidation.parsePrintRequestId(printRequestId);
        try (var download = access.openCurrentFile(requestId)) {
            response.setContentType(download.contentType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + download.filename() + "\"");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            download.inputStream().transferTo(response.getOutputStream());
        }
    }

    @PostMapping("/{printRequestId}/request-correction")
    public ApiResponse<PrintRequestTechnicalReviewResponse> requestCorrection(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId,
            @Valid @RequestBody RequestPrintCorrectionRequest input) {
        return ApiResponse.success(reviews.requestCorrection(
                PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId), input));
    }

    @PostMapping("/{printRequestId}/approve-review")
    public ApiResponse<PrintRequestTechnicalReviewResponse> approveReview(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId,
            @Valid @RequestBody ApprovePrintRequestReviewRequest input) {
        return ApiResponse.success(reviews.approve(
                PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId), input));
    }

    @PostMapping("/{printRequestId}/quotes")
    public ResponseEntity<ApiResponse<PrintQuoteResponse>> issueQuote(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId,
            @Valid @RequestBody CreatePrintQuoteRequest input) {
        PrintQuoteResponse created = quotes.issueQuote(
                PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId), input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Print Quote issued successfully", created));
    }

    @GetMapping("/{printRequestId}/quotes")
    public ApiResponse<List<PrintQuoteResponse>> listQuotes(@PathVariable String printRequestId) {
        return ApiResponse.success(quotes.listStaffQuotes(
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }
}
