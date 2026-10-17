package com.project.w_3d_world.features.auth.controller;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.auth.dto.request.EmailRequest;
import com.project.w_3d_world.features.auth.dto.request.ResetPasswordRequest;
import com.project.w_3d_world.features.auth.dto.request.VerifyEmailRequest;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.service.EmailTokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Email verification and password reset")
public class EmailAuthController {
    private final EmailTokenService emailTokenService;

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email using the one-time token from the email link")
    public ApiResponse<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        emailTokenService.verifyEmail(request.email(), request.token());
        return new ApiResponse<>(200, "Email verified successfully.", null, OffsetDateTime.now());
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Request a new verification link; the old token becomes invalid")
    public ApiResponse<Void> resend(@Valid @RequestBody EmailRequest request) {
        emailTokenService.requestLink(request.email(), TokenPurpose.VERIFY_EMAIL);
        return genericResponse();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link without exposing account existence")
    public ApiResponse<Void> forgot(@Valid @RequestBody EmailRequest request) {
        emailTokenService.requestLink(request.email(), TokenPurpose.RESET_PASSWORD);
        return genericResponse();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the one-time reset token")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        emailTokenService.resetPassword(request.email(), request.token(), request.newPassword());
        return new ApiResponse<>(
                200, "Password changed successfully. You can log in again.", null, OffsetDateTime.now());
    }

    private ApiResponse<Void> genericResponse() {
        return new ApiResponse<>(
                200,
                "If the account is eligible, instructions will be sent by email. If you just requested a link,"
                    + " please wait before requesting another link.",
                null,
                OffsetDateTime.now());
    }
}
