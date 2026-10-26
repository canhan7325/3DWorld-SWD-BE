package com.project.w_3d_world.features.auth.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.auth.service.SellerVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Seller verification",
        description = "Submit and review seller eligibility. Verification does not activate a paid "
                + "subscription.")
public class SellerVerificationController {
    private final SellerVerificationService service;
    private final AccountAuthorization authorization;

    @GetMapping("/users/me/seller-verification")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Read own seller verification",
            description = "Return contact/bank eligibility and the current verification decision.")
    public ApiResponse<SellerVerificationService.Response> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.get(user.userId()));
    }

    @PostMapping("/users/me/seller-verification")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Submit or resubmit seller verification",
            description = "Requires verified email, phone and an owned VERIFIED active bank account. Does "
                    + "not grant Seller role or charge subscription.")
    public ApiResponse<SellerVerificationService.Response> submit(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitRequest request) {
        return ApiResponse.success(service.submit(user.userId(), request.bankAccountId(), request.evidenceReference()));
    }

    @GetMapping("/admin/seller-verifications")
    @PreAuthorize("hasAnyRole('ADMIN','PRINT_STAFF')")
    @Operation(summary = "List seller verification queue",
            description = "Paginated active accounts filtered by verification status for authorized reviewers.")
    public ApiResponse<org.springframework.data.domain.Page<SellerVerificationService.Response>> queue(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.queue(reviewer.userId(), status, page, size));
    }

    @GetMapping("/admin/users/{id}/seller-verification")
    @PreAuthorize("hasAnyRole('ADMIN','PRINT_STAFF')")
    @Operation(
            summary = "Read a seller verification request",
            description = "Authorized reviewer can read pending evidence and the submission timestamp used "
                    + "for optimistic review.")
    public ApiResponse<SellerVerificationService.Response> pending(@AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable int id) {
        authorization.requireRole(reviewer.userId(), RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        return ApiResponse.success(service.get(id));
    }

    @PatchMapping("/admin/users/{id}/seller-verification")
    @PreAuthorize("hasAnyRole('ADMIN','PRINT_STAFF')")
    @Operation(
            summary = "Approve or reject seller verification",
            description = "Requires the current submittedAt value and a reason; stale/self reviews are "
                    + "rejected. Eligibility is checked again before approval.")
    public ApiResponse<SellerVerificationService.Response> review(@AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable int id, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(service.review(reviewer.userId(), id, request.submittedAt(),
                request.approved(), request.reason()));
    }

    public record SubmitRequest(@NotNull @Positive Integer bankAccountId,
                                @NotBlank @Size(max=500) String evidenceReference) {}
    public record ReviewRequest(@Positive long submittedAt, @NotNull Boolean approved,
                                @NotBlank @Size(max=1000) String reason) {}
}
