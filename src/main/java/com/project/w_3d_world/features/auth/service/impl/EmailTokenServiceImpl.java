package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.util.EmailTokenCodec;
import com.project.w_3d_world.features.auth.entity.EmailToken;
import com.project.w_3d_world.features.auth.enums.DeliveryStatus;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.repository.EmailTokenRepository;
import com.project.w_3d_world.features.auth.service.EmailTokenService;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailTokenServiceImpl implements EmailTokenService {
    private final UserRepository userRepository;
    private final EmailTokenRepository tokenRepository;
    private final EmailTokenCodec tokenCodec;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.token-expiration}")
    private long expiration;

    @Value("${app.auth.resend-cooldown-seconds}")
    private long resendCooldown;

    @Override
    @Transactional
    public void createVerification(User user) {
        createToken(user, TokenPurpose.VERIFY_EMAIL);
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
        if (tokenRepository.existsByUser_IdAndPurposeAndCreatedAtAfter(
                user.getId(), purpose, Instant.now().minusSeconds(resendCooldown))) {
            return;
        }
        createToken(user, purpose);
    }

    private void createToken(User user, TokenPurpose purpose) {
        if (expiration <= 0 || resendCooldown <= 0) {
            throw new IllegalStateException(
                    "Email token expiration and resend cooldown must be positive");
        }
        tokenRepository.invalidateUnused(
                user.getId(), purpose, Instant.now(), DeliveryStatus.CANCELLED);
        String rawToken = tokenCodec.generate();
        var token =
                new EmailToken(
                        user,
                        purpose,
                        tokenCodec.hash(rawToken),
                        Instant.now().plusMillis(expiration));
        tokenRepository.saveAndFlush(token);
        token.setEncryptedToken(tokenCodec.encrypt(rawToken, token.getId().toString()));
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
            throw ApiException.invalidRequest("Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        User user = consume(email, rawToken, TokenPurpose.RESET_PASSWORD);
        user.changePassword(passwordEncoder.encode(newPassword));
        tokenRepository.invalidateUnused(
                user.getId(), TokenPurpose.RESET_PASSWORD, Instant.now(), DeliveryStatus.CANCELLED);
    }

    private User consume(String email, String rawToken, TokenPurpose purpose) {
        String recipient = normalizeEmail(email);
        User user = userRepository.lockByNormalizedEmail(recipient).orElseThrow(this::invalidToken);
        EmailToken token =
                tokenRepository
                        .lockByHash(tokenCodec.hash(rawToken), user.getId(), purpose)
                        .orElseThrow(this::invalidToken);
        if (!user.isActive()
                || !token.getUser().getId().equals(user.getId())
                || !token.canBeUsed(recipient, purpose)) {
            throw invalidToken();
        }
        token.consume();
        return user;
    }

    private ApiException invalidToken() {
        return ApiException.invalidRequest(
                "Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
