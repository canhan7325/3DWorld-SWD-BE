package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.service.EmailTokenService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTokenServiceImpl implements EmailTokenService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessionService;

    @Value("${app.auth.token-expiration}")
    private long expiration;

    @Value("${app.auth.base-url}")
    private String baseUrl;

    @Override
    @Transactional
    public void createVerification(User user) {
        sendLink(user, TokenPurpose.VERIFY_EMAIL);
    }

    @Override
    @Transactional
    public void requestLink(String email, TokenPurpose purpose) {
        var found = userRepository.lockByNormalizedEmail(normalizeEmail(email));
        if (found.isEmpty() || !found.get().isActive()) {
            return;
        }
        User user = found.get();
        if (purpose == TokenPurpose.VERIFY_EMAIL && user.isEmailVerified()) {
            return;
        }
        sendLink(user, purpose);
    }

    private void sendLink(User user, TokenPurpose purpose) {
        if (expiration <= 0) {
            throw new IllegalStateException("Email token expiration must be positive");
        }
        user.revokeSessions();
        userRepository.saveAndFlush(user);
        String rawToken = jwtService.generateEmailToken(
                user.getId(), user.getEmail(), purpose, expiration).getTokenValue();
        String path = purpose == TokenPurpose.VERIFY_EMAIL ? "/verify-email" : "/reset-password";
        String link = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("email", "{email}")
                .queryParam("token", "{token}")
                .encode().buildAndExpand(user.getEmail(), rawToken).toUriString();
        try {
            emailService.sendLink(user.getEmail(),
                    purpose == TokenPurpose.VERIFY_EMAIL ? "Verify your email" : "Reset your password",
                    "This link expires and can only be used once.", link);
        } catch (RuntimeException exception) {
            // No outbox table: keep the account unchanged and let the caller request a new link.
            log.warn("Authentication email delivery failed for {}", user.getEmail(), exception);
        }
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String rawToken) {
        User user = consume(email, rawToken, TokenPurpose.VERIFY_EMAIL);
        user.verifyEmail();
    }

    @Override
    @Transactional
    public void resetPassword(String email, String rawToken, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalidRequest("Password must not exceed 72 UTF-8 bytes.");
        }
        User user = consume(email, rawToken, TokenPurpose.RESET_PASSWORD);
        user.changePassword(passwordEncoder.encode(newPassword));
        sessionService.revokeAllSessions(user.getId());
    }

    private User consume(String email, String rawToken, TokenPurpose purpose) {
        String recipient = normalizeEmail(email);
        User user = userRepository.lockByNormalizedEmail(recipient).orElseThrow(this::invalidToken);
        Jwt token;
        try {
            token = jwtService.validateEmailToken(rawToken, purpose);
            if (!Integer.toString(user.getId()).equals(token.getSubject())
                    || !recipient.equalsIgnoreCase(token.getClaimAsString("email"))
                    || (user.getUpdatedAt() != null && !token.getIssuedAt().isAfter(user.getUpdatedAt()))) {
                throw invalidToken();
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
        if (!user.isActive() || (purpose == TokenPurpose.VERIFY_EMAIL && user.isEmailVerified())) {
            throw invalidToken();
        }
        return user;
    }

    private ApiException invalidToken() {
        return ApiException.invalidRequest(
                "The link is invalid, expired, or has already been used.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
