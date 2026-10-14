package com.project.w_3d_world.features.auth.dto.response;

import java.time.Instant;

public record LoginResponse(
        String accessToken, String tokenType, Instant expiresAt, UserResponse user) {}
