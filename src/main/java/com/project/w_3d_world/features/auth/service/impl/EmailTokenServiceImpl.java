package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.delivery.*;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.service.EmailTokenService;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTokenServiceImpl implements EmailTokenService {
    private final UserRepository users;
    private final JwtService jwt;
    private final EmailStateCodec codec;
    private final EmailTokenEncryption encryption;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    @Value("${app.auth.token-expiration}")
    private long expiration;
    @Value("${app.auth.email-resend-cooldown-seconds:60}")
    private long cooldown;

    @Override
    @Transactional
    public void createVerification(User user) {
        queue(user, TokenPurpose.VERIFY_EMAIL);
    }

    @Override
    @Transactional
    public void requestLink(String email, TokenPurpose purpose) {
        var user = users.lockByNormalizedEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || !user.isActive()
                || (purpose == TokenPurpose.VERIFY_EMAIL && user.isEmailVerified())) return;
        var previous = codec.read(user.getEmailDeliveryState()).get(purpose.name());
        long now = System.currentTimeMillis();
        if (previous != null && previous.createdAt() + cooldown * 1000 > now
                && previous.expiresAt() > now
                && !("FAILED".equals(previous.status()) || "EXHAUSTED".equals(previous.status())
                    || "CANCELLED".equals(previous.status()))) {
            return;
        }
        queue(user, purpose);
    }

    private void queue(User user, TokenPurpose purpose) {
        long now = System.currentTimeMillis();
        long version = user.nextEmailVersion(purpose);
        String token = jwt.generateEmailToken(user.getId(), user.getEmail(), purpose, expiration, version)
                .getTokenValue();
        String reference = UUID.randomUUID().toString();
        var states = codec.read(user.getEmailDeliveryState());
        states.put(purpose.name(), new EmailDeliveryState(reference, user.getEmail(), version,
                encryption.encrypt(token, reference), now, now + expiration, "PENDING", 0, now, null, null));
        user.emailDeliveryState(codec.write(states));
        users.saveAndFlush(user);
        events.publishEvent(new EmailDeliveryRequested(user.getId()));
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String token) {
        User user = consume(email, token, TokenPurpose.VERIFY_EMAIL);
        if (user.isEmailVerified()) {
            throw invalidToken();
        }
        user.verifyEmail();
    }

    @Override
    @Transactional
    public void resetPassword(String email, String token, String newPassword) {
        AccountValidation.password(newPassword);
        User user = consume(email, token, TokenPurpose.RESET_PASSWORD);
        user.changePassword(passwordEncoder.encode(newPassword));
        user.revokeSessions();
    }

    private User consume(String email, String raw, TokenPurpose purpose) {
        User user = users.lockByNormalizedEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(this::invalidToken);
        try {
            var token = jwt.validateEmailToken(raw, purpose);
            var states = codec.read(user.getEmailDeliveryState());
            var delivery = states.get(purpose.name());
            long version = ((Number) token.getClaim("emailVersion")).longValue();
            if (!user.isActive() || !user.getEmail().equalsIgnoreCase(token.getClaimAsString("email"))
                    || !user.getId().toString().equals(token.getSubject())
                    || version != user.emailVersion(purpose) || delivery == null
                    || delivery.version() != version || !"SENT".equals(delivery.status())
                    || delivery.expiresAt() <= System.currentTimeMillis()) {
                throw invalidToken();
            }
            user.nextEmailVersion(purpose);
            states.put(purpose.name(), delivery.result("USED", null, delivery.attempts(),
                    System.currentTimeMillis(), delivery.deliveredAt(), null));
            user.emailDeliveryState(codec.write(states));
            return user;
        } catch (RuntimeException exception) {
            throw invalidToken();
        }
    }

    private ApiException invalidToken() {
        return ApiException.invalidRequest("The link is invalid, expired, or has already been used.");
    }
}
