package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/print/requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer Print Requests")
public class PrintRequestController {
    private final PrintRequestService service;

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
}
