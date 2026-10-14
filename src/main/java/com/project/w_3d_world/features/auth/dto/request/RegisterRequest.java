package com.project.w_3d_world.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
                @Email(message = "Email is invalid")
                @Size(max = 320)
                @Schema(example = "user@example.com")
                String email,
        @NotBlank(message = "Password is required")
                @Size(min = 8, max = 72, message = "Password must have 8–72 characters")
                @Schema(
                        example = "StrongPassword!123",
                        description = "At least 8 characters and at most 72 UTF-8 bytes")
                String password,
        @NotBlank(message = "Full name is required")
                @Size(max = 255)
                @Schema(example = "Nguyen Van A")
                String fullName,
        @Pattern(
                        regexp = "^\\+?[0-9]{8,15}$",
                        message = "Phone must have 8–15 digits, optionally prefixed with +")
                @Schema(example = "0901234567", nullable = true)
                String phone) {}
