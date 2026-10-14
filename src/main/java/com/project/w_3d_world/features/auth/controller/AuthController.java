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

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
                .body(ApiResponse.created("Đăng ký thành công", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT access token")
    @SecurityRequirements
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current account")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<UserResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(authService.currentUser(user.userId()));
    }
}
