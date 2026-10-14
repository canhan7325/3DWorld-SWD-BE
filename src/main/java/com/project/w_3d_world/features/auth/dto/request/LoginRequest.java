package com.project.w_3d_world.features.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Email @Size(max = 320) @Schema(example = "user@example.com") String email,
        @NotBlank @Size(max = 72) @Schema(example = "StrongPassword!123") String password) {}
