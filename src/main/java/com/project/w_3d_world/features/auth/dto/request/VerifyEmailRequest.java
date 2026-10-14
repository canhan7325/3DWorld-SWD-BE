package com.project.w_3d_world.features.auth.dto.request;

import jakarta.validation.constraints.*;

public record VerifyEmailRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
