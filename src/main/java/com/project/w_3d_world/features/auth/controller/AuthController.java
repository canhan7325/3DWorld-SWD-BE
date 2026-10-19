package com.project.w_3d_world.features.auth.controller;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.features.auth.dto.request.LoginRequest;
import com.project.w_3d_world.features.auth.dto.request.RegisterRequest;
import com.project.w_3d_world.features.auth.dto.response.LoginResponse;
import com.project.w_3d_world.features.auth.dto.response.UserResponse;
import com.project.w_3d_world.features.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a customer account")
    @SecurityRequirements
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Registration successful", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT access token")
    @SecurityRequirements
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout and revoke the current JWT session")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<Void> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(user);
        return new ApiResponse<>(200, "Logged out successfully.", null, OffsetDateTime.now());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the current account")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(authService.currentUser(user.userId()));
    }
}
