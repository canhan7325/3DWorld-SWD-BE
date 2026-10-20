package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintMaterialResponse;
import com.project.w_3d_world.features.printing.service.PrintMaterialService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/print/materials")
@RequiredArgsConstructor
public class PrintMaterialController {
    private final PrintMaterialService service;

    @GetMapping
    public ApiResponse<PagedPrintMaterialResponse> list(
            @RequestParam(required = false) String technologyCode,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        if (technologyCode != null && (technologyCode.isBlank() || technologyCode.length() > 30
                || !technologyCode.equals(technologyCode.strip()))) {
            throw ApiException.invalidRequest("Technology code must have 1 to 30 characters without surrounding whitespace.");
        }
        int pageNumber = parseInteger(page, "page");
        int pageSize = parseInteger(size, "size");
        if (pageNumber < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (pageSize < 1 || pageSize > 50) {
            throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        }
        return ApiResponse.success(service.getActiveMaterials(
                technologyCode, pageNumber, pageSize));
    }

    @GetMapping("/{materialId}")
    public ApiResponse<PrintMaterialResponse> detail(@PathVariable String materialId) {
        int id = parseInteger(materialId, "materialId");
        if (id <= 0) throw ApiException.invalidRequest("Material ID must be a positive integer.");
        return ApiResponse.success(service.getActiveMaterialById(id));
    }

    private static int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw ApiException.invalidRequest(name + " must be a valid integer.");
        }
    }
}
