package com.project.w_3d_world.features.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.delivery.*;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.service.impl.EmailTokenServiceImpl;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDeliveryTests {
    private final UserRepository users = mock(UserRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EmailStateCodec codec = new EmailStateCodec();
    private EmailTokenServiceImpl tokens;
    private EmailDeliveryService deliveries;
    private JwtService jwt;
    private User user;

    @BeforeEach
    void setup() throws Exception {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        jwt = new JwtService(key, 60000);
        var encryption = new EmailTokenEncryption(key);
        tokens = new EmailTokenServiceImpl(users, jwt, codec, encryption, mock(PasswordEncoder.class), events);
        deliveries = new EmailDeliveryService(users, codec, encryption, email, mock(JdbcTemplate.class), jwt);
        ReflectionTestUtils.setField(tokens, "expiration", 60000L);
        ReflectionTestUtils.setField(tokens, "cooldown", 60L);
        ReflectionTestUtils.setField(deliveries, "expiration", 60000L);
        ReflectionTestUtils.setField(deliveries, "maxAttempts", 5);
        ReflectionTestUtils.setField(deliveries, "baseUrl", "http://localhost:8080");
        user = new User("buyer@example.com", "hash", "Buyer", "0901234567");
        ReflectionTestUtils.setField(user, "id", 10);
        when(users.lockById(10)).thenReturn(Optional.of(user));
        when(users.lockByNormalizedEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void delayedQueueGetsFreshValidityAtDeliveryAndRemainsOneTime() {
        tokens.createVerification(user);
        var state = state();
        user.emailDeliveryState(codec.write(Map.of("VERIFY_EMAIL",
                state.prepared(state.encryptedToken(), System.currentTimeMillis() - 3600000))));
        long beforeSend = System.currentTimeMillis();
        deliveries.dispatch(10);
        assertEquals("SENT", state().status());
        assertTrue(state().expiresAt() >= beforeSend + 59000);
        String token = deliveredTokens().getFirst();
        assertEquals("10", jwt.validateEmailToken(token, TokenPurpose.VERIFY_EMAIL).getSubject());
        tokens.verifyEmail(user.getEmail(), token);
        assertTrue(user.isEmailVerified());
        assertThrows(ApiException.class, () -> tokens.verifyEmail(user.getEmail(), token));
        verify(events).publishEvent(new EmailDeliveryRequested(10));
    }

    @Test
    void resendWorksAfterCooldownAndRejectsOnlyTheSupersededLink() {
        tokens.createVerification(user);
        deliveries.dispatch(10);
        String oldToken = deliveredTokens().getFirst();
        long version = user.emailVersion(TokenPurpose.VERIFY_EMAIL);
        tokens.requestLink(user.getEmail(), TokenPurpose.VERIFY_EMAIL);
        assertEquals(version, user.emailVersion(TokenPurpose.VERIFY_EMAIL));
        expireCooldown();
        tokens.requestLink(user.getEmail(), TokenPurpose.VERIFY_EMAIL);
        deliveries.dispatch(10);
        assertEquals(version + 1, user.emailVersion(TokenPurpose.VERIFY_EMAIL));
        var sent = deliveredTokens();
        assertEquals(2, sent.size());
        assertThrows(ApiException.class, () -> tokens.verifyEmail(user.getEmail(), oldToken));
        tokens.verifyEmail(user.getEmail(), sent.getLast());
        assertTrue(user.isEmailVerified());
    }

    @Test
    void failedOrExpiredDeliveryCanBeRequestedAgain() {
        tokens.createVerification(user);
        doThrow(new MailSendException("Test provider failure")).doNothing()
                .when(email).sendLink(anyString(), anyString(), anyString(), anyString());
        deliveries.dispatch(10);
        assertEquals("FAILED", state().status());
        assertFalse(user.isEmailVerified());
        tokens.requestLink(user.getEmail(), TokenPurpose.VERIFY_EMAIL);
        deliveries.dispatch(10);
        assertEquals("SENT", state().status());
        tokens.verifyEmail(user.getEmail(), deliveredTokens().getLast());
        assertTrue(user.isEmailVerified());
    }

    private EmailDeliveryState state() {
        return codec.read(user.getEmailDeliveryState()).get("VERIFY_EMAIL");
    }

    private void expireCooldown() {
        var state = state();
        user.emailDeliveryState(codec.write(Map.of("VERIFY_EMAIL", new EmailDeliveryState(state.reference(),
                state.recipient(), state.version(), state.encryptedToken(), System.currentTimeMillis() - 61000,
                state.expiresAt(), state.status(), state.attempts(), state.nextAttemptAt(), state.deliveredAt(), state.lastError()))));
    }

    private List<String> deliveredTokens() {
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).sendLink(eq(user.getEmail()), anyString(), anyString(), links.capture());
        return links.getAllValues().stream().map(link -> Arrays.stream(URI.create(link).getRawQuery().split("&"))
                .filter(part -> part.startsWith("token=")).findFirst().map(part ->
                        URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8)).orElseThrow()).toList();
    }
}
