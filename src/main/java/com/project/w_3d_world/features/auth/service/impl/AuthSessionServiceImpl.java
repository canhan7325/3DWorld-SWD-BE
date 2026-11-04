package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.auth.service.SessionStateCache;
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
        if (sessionId == null || issuedAt == null) {
            throw ApiException.invalidSession();
        }
        // Dùng lại trạng thái vừa đọc (tối đa SessionStateCache.TTL) thay vì truy vấn users ở mọi request.
        SessionStateCache.State state = SessionStateCache.get(userId);
        if (state == null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                throw ApiException.invalidSession();
            }
            SessionStateCache.put(userId, user.isActive(), user.getAuthVersion());
            state = SessionStateCache.get(userId);
            if (state == null) {
                state = new SessionStateCache.State(user.isActive(), user.getAuthVersion(), System.nanoTime());
            }
        }
        if (!state.active() || sessionId.getMostSignificantBits() != state.authVersion()) {
            throw ApiException.invalidSession();
        }
    }

    @Override
    @Transactional
    public void revokeSession(int userId, UUID sessionId) {
        SessionStateCache.evict(userId);
        userRepository.lockById(userId).ifPresent(user -> {
            user.revokeSessions();
            userRepository.saveAndFlush(user);
        });
        SessionStateCache.evict(userId);
    }

    @Override
    @Transactional
    public void revokeAllSessions(int userId) {
        SessionStateCache.evict(userId);
        userRepository.lockById(userId).ifPresent(user -> {
            user.revokeSessions();
            userRepository.saveAndFlush(user);
        });
        SessionStateCache.evict(userId);
    }
}
