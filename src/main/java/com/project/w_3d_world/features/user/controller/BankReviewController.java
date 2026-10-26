package com.project.w_3d_world.features.user.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.user.dto.UserDataResponses.BankResponse;
import com.project.w_3d_world.features.user.service.BankVerificationService;
import com.project.w_3d_world.features.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bank-accounts")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Bank verification",
        description = "Admin review of bank account verification evidence.")
public class BankReviewController {
    private final BankVerificationService service;

    @GetMapping
    @Operation(
            summary = "List bank verification queue",
            description = "Admin-only active bank accounts by review status; returns masked details and "
                    + "review fingerprints.")
    public ApiResponse<org.springframework.data.domain.Page<BankResponse>> queue(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.queue(reviewer.userId(), status, page, size));
    }

    @PatchMapping("/{id}/verification")
    @Operation(
            summary = "Review bank account",
            description = "Approve or reject a bank account and write an audit record.")
    public ApiResponse<BankResponse> review(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable int id,
            @jakarta.validation.Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(
                service.review(reviewer.userId(), id, request.approved(), request.reason(),
                        request.evidenceReference(), request.accountFingerprint()));
    }

    @GetMapping("/{id}/verification")
    @Operation(
            summary = "Read bank verification evidence",
            description = "Admin-only masked bank details and review audit evidence.")
    public ApiResponse<BankVerificationService.VerificationResponse> get(
            @AuthenticationPrincipal AuthenticatedUser reviewer, @PathVariable int id) {
        return ApiResponse.success(service.get(reviewer.userId(), id));
    }

    public record ReviewRequest(@NotNull Boolean approved, @NotBlank String reason,
                                @NotBlank String evidenceReference, @NotBlank @jakarta.validation.constraints.Pattern(regexp="[0-9a-f]{64}") String accountFingerprint) {}
}
