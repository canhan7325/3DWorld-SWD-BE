package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintMaterialResponse;
import com.project.w_3d_world.features.printing.service.PrintMaterialService;
import com.project.w_3d_world.features.printing.validation.PrintMaterialRequestValidation;

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

    @GetMapping("/getAll")
    public ApiResponse<PagedPrintMaterialResponse> list(
            @RequestParam(required = false) String technologyCode,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        String validatedTechnology = PrintMaterialRequestValidation.validateTechnology(technologyCode);
        int pageNumber = PrintMaterialRequestValidation.parsePage(page);
        int pageSize = PrintMaterialRequestValidation.parsePageSize(size);
        return ApiResponse.success(service.getActiveMaterials(
                validatedTechnology, pageNumber, pageSize));
    }

    @GetMapping("/getId/{materialId}")
    public ApiResponse<PrintMaterialResponse> detail(@PathVariable String materialId) {
        int id = PrintMaterialRequestValidation.parseCustomerMaterialId(materialId);
        return ApiResponse.success(service.getActiveMaterialById(id));
    }
}
