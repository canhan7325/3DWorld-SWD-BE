package com.project.w_3d_world.features.auth.dto.request;

import jakarta.validation.constraints.*;

public record VerifyEmailRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 20, max = 4096) String token) {}
