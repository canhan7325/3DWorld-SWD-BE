package com.project.w_3d_world.features.user.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.user.dto.UserDataResponses.BankResponse;
import com.project.w_3d_world.features.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bank-accounts")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Bank verification", description = "Admin review of bank account verification evidence.")
public class BankReviewController {
    private final UserProfileService service;

    @PatchMapping("/{id}/verification")
    @Operation(
            summary = "Review bank account",
            description = "Approve or reject a bank account and write an audit record.")
    public ApiResponse<BankResponse> review(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable int id,
            @RequestBody ReviewRequest request) {
        return ApiResponse.success(
                service.reviewBank(reviewer.userId(), id, request.approved(), request.reason()));
    }

    public record ReviewRequest(boolean approved, @NotBlank String reason) {}
}
