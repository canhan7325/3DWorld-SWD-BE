package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintMaterialRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialCostRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialStatusRequest;
import com.project.w_3d_world.features.printing.dto.response.AdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.service.AdminPrintMaterialService;
import com.project.w_3d_world.features.printing.validation.PrintMaterialRequestValidation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import jakarta.persistence.PersistenceException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/print/materials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Print Materials")
public class AdminPrintMaterialController {
    private final AdminPrintMaterialService service;

    @GetMapping("/getAll")
    @Operation(summary = "List all print materials for administrators")
    public ApiResponse<PagedAdminPrintMaterialResponse> list(
            @RequestParam(required = false) String technologyCode,
            @RequestParam(required = false) String isActive,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        int pageNumber = PrintMaterialRequestValidation.parsePage(page);
        int pageSize = PrintMaterialRequestValidation.parsePageSize(size);
        return ApiResponse.success(service.getMaterials(
                PrintMaterialRequestValidation.validateTechnology(technologyCode),
                PrintMaterialRequestValidation.parseActive(isActive),
                pageNumber, pageSize));
    }

    @PostMapping("/create")
    @Operation(summary = "Create a print material")
    public ResponseEntity<ApiResponse<AdminPrintMaterialResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser admin, @RequestBody JsonNode body) {
        AdminPrintMaterialResponse created = service.create(PrintMaterialRequestValidation.requireAdminId(admin),
                CreatePrintMaterialRequest.from(body));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Material created successfully", created));
    }

    @PatchMapping("/update/{materialId}")
    @Operation(summary = "Update a material's estimated cost")
    public ApiResponse<AdminPrintMaterialResponse> updateCost(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable String materialId, @RequestBody JsonNode body) {
        return ApiResponse.success(service.updateCost(PrintMaterialRequestValidation.requireAdminId(admin),
                PrintMaterialRequestValidation.parseMaterialId(materialId),
                UpdatePrintMaterialCostRequest.from(body)));
    }

    @PatchMapping("/updateStatus/{materialId}")
    @Operation(summary = "Activate or deactivate a print material")
    public ApiResponse<AdminPrintMaterialResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable String materialId, @RequestBody JsonNode body) {
        return ApiResponse.success(service.updateStatus(PrintMaterialRequestValidation.requireAdminId(admin),
                PrintMaterialRequestValidation.parseMaterialId(materialId),
                UpdatePrintMaterialStatusRequest.from(body)));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, PersistenceException.class})
    public ResponseEntity<ApiResponse<Void>> persistenceFailure(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Material change could not be completed."));
    }

}
