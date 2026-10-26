package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintQuoteResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestFileVersionResponse;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationRequest;
import com.project.w_3d_world.features.printing.dto.request.ConfirmPrintRequestPreviewRequest;
import com.project.w_3d_world.features.printing.dto.request.CancelPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.RequestPrintQuoteRevisionRequest;
import com.project.w_3d_world.features.printing.dto.request.SubmitPrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.request.SelectPrintRequestShippingAddressRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestShippingAddressResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSpecVersionResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStateResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestStatusHistoryResponse;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileDownloadService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestFileVersionService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestQuoteService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestSpecificationService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestStateService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestWorkflowService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingAddressService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingReadinessService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/print/requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer Print Requests")
public class PrintRequestController {
    private final PrintRequestService service;
    private final PrintRequestFileCreateService fileCreateService;
    private final PrintRequestFileDownloadService fileDownloadService;
    private final PrintRequestFileVersionService fileVersionService;
    private final PrintRequestSpecificationService specificationService;
    private final PrintRequestStateService stateService;
    private final PrintRequestQuoteService quoteService;
    private final PrintRequestWorkflowService workflowService;
    private final PrintRequestShippingAddressService shippingAddressService;
    private final PrintRequestShippingReadinessService shippingReadinessService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo bản nháp yêu cầu in từ tệp và vật liệu")
    public ResponseEntity<ApiResponse<PrintRequestResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @RequestParam Integer materialId,
            @RequestParam Boolean rightsDeclarationAccepted,
            @RequestPart("file") MultipartFile file) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        PrintRequestResponse created = fileCreateService.create(
                customerId, materialId, rightsDeclarationAccepted, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Print Request draft created successfully", created));
    }

    @GetMapping("/getAll")
    @Operation(summary = "Lấy danh sách yêu cầu in của khách hàng")
    public ApiResponse<PagedPrintRequestResponse> getAll(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(service.getMyRequests(customerId,
                PrintRequestValidation.parsePage(page), PrintRequestValidation.parsePageSize(size)));
    }

    @GetMapping("/getId/{printRequestId}")
    @Operation(summary = "Lấy chi tiết yêu cầu in theo ID")
    public ApiResponse<PrintRequestResponse> getById(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        int requestId = PrintRequestValidation.parsePrintRequestId(printRequestId);
        return ApiResponse.success(service.getMyRequestById(customerId, requestId)
                .withWorkflow(workflowService.forCustomer(customerId, requestId))
                .withShipping(shippingReadinessService.forCustomer(customerId, requestId)));
    }

    @PatchMapping("/getId/{printRequestId}/updateShippingAddress")
    @Operation(summary = "Cập nhật địa chỉ giao hàng của yêu cầu in")
    public ApiResponse<PrintRequestShippingAddressResponse> selectShippingAddress(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @Valid @RequestBody SelectPrintRequestShippingAddressRequest body) {
        return ApiResponse.success(shippingAddressService.select(
                PrintRequestValidation.requireCustomerId(customer),
                PrintRequestValidation.parsePrintRequestId(printRequestId), body.addressId()));
    }

    @GetMapping("/getId/{printRequestId}/file")
    @Operation(summary = "Tải tệp in hiện tại của yêu cầu in")
    public void downloadFile(@AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId, HttpServletResponse response) throws IOException {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        int id = PrintRequestValidation.parsePrintRequestId(printRequestId);
        try (var download = fileDownloadService.open(customerId, id)) {
            response.setContentType(download.contentType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + download.filename() + "\"");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            download.inputStream().transferTo(response.getOutputStream());
        }
    }

    @PostMapping(value = "/getId/{printRequestId}/file/versions/create",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo phiên bản tệp in mới để chỉnh sửa yêu cầu")
    public ResponseEntity<ApiResponse<PrintRequestFileVersionResponse>> correctFile(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @RequestParam Integer expectedCurrentFileVersion,
            @RequestPart("file") MultipartFile file) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        int id = PrintRequestValidation.parsePrintRequestId(printRequestId);
        var created = fileVersionService.correct(customerId, id, expectedCurrentFileVersion, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Print Request file version created successfully", created));
    }

    @GetMapping("/getId/{printRequestId}/file/versions/getAll")
    @Operation(summary = "Lấy danh sách phiên bản tệp in")
    public ApiResponse<List<PrintRequestFileVersionResponse>> fileVersions(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(fileVersionService.list(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/getId/{printRequestId}/file/versions/{version}/download")
    @Operation(summary = "Tải một phiên bản tệp in theo số phiên bản")
    public void downloadFileVersion(@AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId, @PathVariable String version,
            HttpServletResponse response) throws IOException {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        int id = PrintRequestValidation.parsePrintRequestId(printRequestId);
        int fileVersion = PrintRequestValidation.parseFileVersion(version);
        try (var download = fileVersionService.download(customerId, id, fileVersion)) {
            response.setContentType(download.contentType());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + download.filename() + "\"");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            download.inputStream().transferTo(response.getOutputStream());
        }
    }

    @PatchMapping("/getId/{printRequestId}/updateSpecification")
    @Operation(summary = "Cập nhật thông số in và tạo phiên bản thông số mới")
    public ApiResponse<PrintRequestSpecVersionResponse> updateSpecification(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @Valid @RequestBody UpdatePrintRequestSpecificationRequest body) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(specificationService.update(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId), body));
    }

    @GetMapping("/getId/{printRequestId}/specifications/versions/getAll")
    @Operation(summary = "Lấy danh sách phiên bản thông số in")
    public ApiResponse<List<PrintRequestSpecVersionResponse>> specificationVersions(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(specificationService.history(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @PostMapping("/getId/{printRequestId}/preview/confirm")
    @Operation(summary = "Xác nhận xem trước tệp và thông số in hiện tại")
    public ApiResponse<PrintRequestStateResponse> confirmPreview(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @Valid @RequestBody ConfirmPrintRequestPreviewRequest body) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(stateService.confirmPreview(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId), body));
    }

    @PostMapping("/getId/{printRequestId}/submit")
    @Operation(summary = "Gửi yêu cầu in để nhân viên thẩm định")
    public ApiResponse<PrintRequestStateResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @Valid @RequestBody SubmitPrintRequestRequest body) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(stateService.submit(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId), body));
    }

    @PostMapping("/getId/{printRequestId}/cancel")
    @Operation(summary = "Hủy yêu cầu in chưa bị khóa")
    public ApiResponse<PrintRequestStateResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @Valid @RequestBody CancelPrintRequestRequest body) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(stateService.cancel(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId), body));
    }

    @GetMapping("/getId/{printRequestId}/status-history/getAll")
    @Operation(summary = "Lấy lịch sử trạng thái của yêu cầu in")
    public ApiResponse<List<PrintRequestStatusHistoryResponse>> statusHistory(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(stateService.history(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/getId/{printRequestId}/quotes/getAll")
    @Operation(summary = "Lấy danh sách báo giá của yêu cầu in")
    public ApiResponse<List<PrintQuoteResponse>> quotes(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(quoteService.listCustomerQuotes(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @PostMapping("/getId/{printRequestId}/quotes/{quoteId}/accept")
    @Operation(summary = "Chấp nhận báo giá hiện tại của yêu cầu in")
    public ApiResponse<PrintQuoteResponse> acceptQuote(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @PathVariable String quoteId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(quoteService.acceptQuote(
                customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId),
                PrintRequestValidation.parseQuoteId(quoteId)));
    }

    @PostMapping("/getId/{printRequestId}/quotes/{quoteId}/request-revision")
    @Operation(summary = "Yêu cầu nhân viên sửa báo giá")
    public ApiResponse<PrintQuoteResponse> requestQuoteRevision(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId,
            @PathVariable String quoteId,
            @Valid @RequestBody RequestPrintQuoteRevisionRequest body) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(quoteService.requestRevision(
                customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId),
                PrintRequestValidation.parseQuoteId(quoteId),
                body));
    }
}
