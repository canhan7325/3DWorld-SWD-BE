package com.project.w_3d_world.features.auth.delivery;

import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {
    private final UserRepository users;
    private final EmailStateCodec codec;
    private final EmailTokenEncryption encryption;
    private final EmailService email;
    private final JdbcTemplate jdbc;
    private final JwtService jwt;

    @Value("${app.auth.base-url}")
    private String baseUrl;
    @Value("${app.auth.email-max-attempts:5}")
    private int maxAttempts;
    @Value("${app.auth.token-expiration}")
    private long expiration;

    public List<Integer> pendingUsers() {
        return jdbc.queryForList("""
                SELECT user_id FROM users
                WHERE jsonb_path_exists(email_delivery_state,
                    '$.* ? ((@.status == "PENDING" || @.status == "FAILED") && @.nextAttemptAt <= $now)',
                    jsonb_build_object('now', cast(? as bigint)))
                ORDER BY user_id LIMIT 20
                """, Integer.class, System.currentTimeMillis());
    }

    @Transactional
    public void dispatch(int userId) {
        var user = users.lockById(userId).orElse(null);
        if (user == null) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        var states = codec.read(user.getEmailDeliveryState());
        for (var entry : states.entrySet()) {
            EmailDeliveryState value = entry.getValue();
            if (!("PENDING".equals(value.status()) || "FAILED".equals(value.status()))
                    || value.nextAttemptAt() > now) continue;
            TokenPurpose purpose = TokenPurpose.valueOf(entry.getKey());
            if (!user.isActive() || !user.getEmail().equals(value.recipient())
                    || user.emailVersion(purpose) != value.version()
                    || (purpose == TokenPurpose.VERIFY_EMAIL && user.isEmailVerified())) {
                entry.setValue(value.result("CANCELLED", null, value.attempts(), now, null, null));
                continue;
            }
            int attempt = value.attempts() + 1;
            try {
                // Queue/retry time must not consume the recipient's verification window.
                var freshToken = jwt.generateEmailToken(user.getId(), value.recipient(), purpose, expiration, value.version());
                String token = freshToken.getTokenValue();
                value = value.prepared(encryption.encrypt(token, value.reference()), freshToken.getExpiresAt().toEpochMilli());
                boolean verify = purpose == TokenPurpose.VERIFY_EMAIL;
                String link = UriComponentsBuilder.fromUriString(baseUrl)
                        .path(verify ? "/verify-email" : "/reset-password")
                        .queryParam("email", "{email}").queryParam("token", "{token}")
                        .encode().buildAndExpand(value.recipient(), token).toUriString();
                email.sendLink(value.recipient(), verify ? "Verify your email" : "Reset your password",
                        "This link is valid for " + Math.max(1, expiration / 60000)
                                + " minutes and can only be used once. Use the newest email if you request another link.", link);
                long sentAt = System.currentTimeMillis();
                entry.setValue(value.result("SENT", null, attempt, sentAt, sentAt, null));
            } catch (RuntimeException exception) {
                boolean exhausted = attempt >= maxAttempts;
                entry.setValue(value.result(exhausted ? "EXHAUSTED" : "FAILED",
                        exhausted ? null : value.encryptedToken(), attempt,
                        now + Math.min(300_000L, 10_000L << Math.min(attempt - 1, 5)),
                        null, exception.getClass().getSimpleName()));
                log.warn("Authentication email delivery failed for reference {} (attempt {})",
                        value.reference(), attempt);
            }
        }
        user.emailDeliveryState(codec.write(states));
    }
}
