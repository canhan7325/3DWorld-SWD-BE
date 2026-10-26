package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.request.ApprovePrintRequestReviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintQuoteRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintCorrectionRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedStaffReviewQueueResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffReviewQueueItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestTechnicalReviewResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffPrintRequestResponse;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestQuoteService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffAccessService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStaffQueueService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestTechnicalReviewService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/print/requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PRINT_STAFF','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Staff Print Requests")
public class StaffPrintRequestController {
    private final PrintRequestStaffAccessService access;
    private final PrintRequestStaffQueueService queue;
    private final PrintRequestTechnicalReviewService reviews;
    private final PrintRequestQuoteService quotes;

    @GetMapping("/getAll")
    @Operation(summary = "Lấy danh sách yêu cầu in cần nhân viên xử lý")
    public ApiResponse<PagedStaffReviewQueueResponse> list(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @RequestParam(defaultValue = "ALL") String assignment,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        return ApiResponse.success(queue.list(PrintRequestValidation.requireCustomerId(staff),
                assignment, PrintRequestValidation.parsePage(page),
                PrintRequestValidation.parsePageSize(size)));
    }

    @PostMapping("/{printRequestId}/claim")
    @Operation(summary = "Cập nhật phân công: nhận xử lý yêu cầu in")
    public ApiResponse<StaffReviewQueueItemResponse> claim(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId) {
        return ApiResponse.success(queue.claim(PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/getId/{printRequestId}")
    @Operation(summary = "Lấy chi tiết yêu cầu in cho nhân viên theo ID")
    public ApiResponse<StaffPrintRequestResponse> detail(@PathVariable String printRequestId) {
        return ApiResponse.success(access.detail(
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/getId/{printRequestId}/file/download")
    @Operation(summary = "Tải tệp hiện tại của yêu cầu in để thẩm định")
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
    @Operation(summary = "Tạo yêu cầu chỉnh sửa tệp hoặc thông số in")
    public ApiResponse<PrintRequestTechnicalReviewResponse> requestCorrection(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId,
            @Valid @RequestBody RequestPrintCorrectionRequest input) {
        return ApiResponse.success(reviews.requestCorrection(
                PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId), input));
    }

    @PostMapping("/{printRequestId}/approve-review")
    @Operation(summary = "Tạo kết quả duyệt kỹ thuật cho phiên bản hiện tại")
    public ApiResponse<PrintRequestTechnicalReviewResponse> approveReview(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId,
            @Valid @RequestBody ApprovePrintRequestReviewRequest input) {
        return ApiResponse.success(reviews.approve(
                PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId), input));
    }

    @PostMapping("/{printRequestId}/quotes/create")
    @Operation(summary = "Tạo báo giá cho yêu cầu in")
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

    @GetMapping("/getId/{printRequestId}/quotes/getAll")
    @Operation(summary = "Lấy tất cả phiên bản báo giá của yêu cầu in")
    public ApiResponse<List<PrintQuoteResponse>> listQuotes(@PathVariable String printRequestId) {
        return ApiResponse.success(quotes.listStaffQuotes(
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }
}
