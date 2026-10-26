package com.project.w_3d_world.features.printing.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedStaffProductionBoardResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintProductionItemTransitionResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffProductionDetailResponse;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestProductionManagementService;
import com.project.w_3d_world.features.printing.service.impl.PrintRequestShippingReadinessService;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/print/production")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PRINT_STAFF','ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Staff Print Production")
public class StaffPrintProductionController {
    private final PrintRequestProductionManagementService production;
    private final PrintRequestShippingReadinessService shippingReadiness;

    @GetMapping("/getAll")
    @Operation(summary = "Lấy danh sách yêu cầu in trên bảng sản xuất")
    public ApiResponse<PagedStaffProductionBoardResponse> board(
            @RequestParam(defaultValue = "ALL") String itemStatus,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        return ApiResponse.success(production.board(itemStatus,
                PrintRequestValidation.parsePage(page), PrintRequestValidation.parsePageSize(size)));
    }

    @GetMapping("/getId/{printRequestId}")
    @Operation(summary = "Lấy chi tiết sản xuất của yêu cầu in theo ID")
    public ApiResponse<StaffProductionDetailResponse> detail(@PathVariable String printRequestId) {
        int id = PrintRequestValidation.parsePrintRequestId(printRequestId);
        return ApiResponse.success(production.detail(id).withShipping(shippingReadiness.forStaff(id)));
    }

    @PostMapping("/{printRequestId}/items/{itemId}/start")
    @Operation(summary = "Cập nhật trạng thái mục in thành đang sản xuất")
    public ApiResponse<PrintProductionItemTransitionResponse> start(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId, @PathVariable String itemId) {
        return ApiResponse.success(production.start(PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId),
                PrintRequestValidation.parseItemId(itemId)));
    }

    @PostMapping("/{printRequestId}/items/{itemId}/complete")
    @Operation(summary = "Cập nhật trạng thái mục in thành hoàn tất")
    public ApiResponse<PrintProductionItemTransitionResponse> complete(
            @AuthenticationPrincipal AuthenticatedUser staff,
            @PathVariable String printRequestId, @PathVariable String itemId) {
        return ApiResponse.success(production.complete(PrintRequestValidation.requireCustomerId(staff),
                PrintRequestValidation.parsePrintRequestId(printRequestId),
                PrintRequestValidation.parseItemId(itemId)));
    }
}
