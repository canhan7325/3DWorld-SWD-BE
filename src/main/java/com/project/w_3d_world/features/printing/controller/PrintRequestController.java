package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestFileVersionResponse;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import com.project.w_3d_world.features.printing.service.PrintRequestFileCreateService;
import com.project.w_3d_world.features.printing.service.PrintRequestFileDownloadService;
import com.project.w_3d_world.features.printing.service.PrintRequestFileVersionService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    public ApiResponse<PagedPrintRequestResponse> getAll(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(service.getMyRequests(customerId,
                PrintRequestValidation.parsePage(page), PrintRequestValidation.parsePageSize(size)));
    }

    @GetMapping("/getId/{printRequestId}")
    public ApiResponse<PrintRequestResponse> getById(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(service.getMyRequestById(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/getId/{printRequestId}/file")
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

    @PostMapping(value = "/getId/{printRequestId}/file/correct",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @GetMapping("/getId/{printRequestId}/file/versions")
    public ApiResponse<List<PrintRequestFileVersionResponse>> fileVersions(
            @AuthenticationPrincipal AuthenticatedUser customer,
            @PathVariable String printRequestId) {
        int customerId = PrintRequestValidation.requireCustomerId(customer);
        return ApiResponse.success(fileVersionService.list(customerId,
                PrintRequestValidation.parsePrintRequestId(printRequestId)));
    }

    @GetMapping("/getId/{printRequestId}/file/versions/{version}/download")
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

    @ExceptionHandler({DataAccessException.class, TransactionException.class, PersistenceException.class})
    public ResponseEntity<ApiResponse<Void>> persistenceFailure(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Print Request could not be completed."));
    }
}
