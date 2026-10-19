package com.project.w_3d_world.features.auth.service;

import com.project.w_3d_world.features.user.entity.User;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthSessionService {
    void createSession(User user, Jwt token);

    void validateSession(int userId, UUID sessionId, java.time.Instant issuedAt);

    void revokeSession(int userId, UUID sessionId);

    void revokeAllSessions(int userId);
}
