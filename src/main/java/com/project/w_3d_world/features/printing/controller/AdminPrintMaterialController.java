package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.service.AdminPrintMaterialService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/print/materials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Print Materials")
public class AdminPrintMaterialController {
    private final AdminPrintMaterialService service;

    @GetMapping
    @Operation(summary = "List all print materials for administrators")
    public ApiResponse<PagedAdminPrintMaterialResponse> list(
            @RequestParam(required = false) String technologyCode,
            @RequestParam(required = false) String isActive,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        int pageNumber = parseInteger(page, "page");
        int pageSize = parseInteger(size, "size");
        if (pageNumber < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (pageSize < 1 || pageSize > 50) throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        return ApiResponse.success(service.getMaterials(
                validateTechnology(technologyCode), parseActive(isActive),
                pageNumber, pageSize));
    }

    private static String validateTechnology(String technologyCode) {
        if (technologyCode != null && (technologyCode.isBlank() || technologyCode.length() > 30
                || !technologyCode.equals(technologyCode.strip()))) {
            throw ApiException.invalidRequest("Technology code must have 1 to 30 characters without surrounding whitespace.");
        }
        return technologyCode;
    }

    private static Boolean parseActive(String value) {
        if (value == null) return null;
        if ("true".equals(value)) return Boolean.TRUE;
        if ("false".equals(value)) return Boolean.FALSE;
        throw ApiException.invalidRequest("isActive must be true or false.");
    }

    private static int parseInteger(String value, String name) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw ApiException.invalidRequest(name + " must be a valid integer.");
        }
    }
}
