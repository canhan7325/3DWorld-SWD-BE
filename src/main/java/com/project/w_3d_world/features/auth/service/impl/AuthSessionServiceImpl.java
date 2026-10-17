package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void createSession(User user, Jwt token) {
        // Sessions are represented by signed JWTs. No session table is required.
    }

    @Override
    @Transactional(readOnly = true)
    public void validateSession(int userId, UUID sessionId, Instant issuedAt) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isActive()
                || (user.getUpdatedAt() != null && issuedAt != null
                    && !issuedAt.isAfter(user.getUpdatedAt()))) {
            throw ApiException.invalidSession();
        }
    }

    @Override
    @Transactional
    public void revokeSession(int userId, UUID sessionId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.revokeSessions();
            userRepository.saveAndFlush(user);
        });
    }

    @Override
    @Transactional
    public void revokeAllSessions(int userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.revokeSessions();
            userRepository.saveAndFlush(user);
        });
    }
}
