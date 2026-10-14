package com.project.w_3d_world.features.auth.service.impl;

import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.util.EmailTokenCodec;
import com.project.w_3d_world.features.auth.enums.DeliveryStatus;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.repository.EmailTokenRepository;
import com.project.w_3d_world.features.auth.service.EmailDeliveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryServiceImpl implements EmailDeliveryService {
    private final EmailTokenRepository tokenRepository;
    private final EmailTokenCodec tokenCodec;
    private final EmailService emailService;

    @Value("${app.auth.base-url}")
    private String baseUrl;

    @Value("${app.mail.max-attempts}")
    private int maxAttempts;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliverNext() {
        var pending =
                tokenRepository.lockPending(
                        List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED),
                        maxAttempts,
                        Instant.now(),
                        PageRequest.of(0, 1));
        if (pending.isEmpty()) {
            return false;
        }
        var token = pending.getFirst();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            token.cancelDelivery();
            return true;
        }

        try {
            String rawToken =
                    tokenCodec.decrypt(token.getEncryptedToken(), token.getId().toString());
            boolean verification = token.getPurpose() == TokenPurpose.VERIFY_EMAIL;
            String path = verification ? "/verify-email" : "/reset-password";
            String link =
                    UriComponentsBuilder.fromUriString(baseUrl)
                            .path(path)
                            .queryParam("email", "{email}")
                            .queryParam("token", "{token}")
                            .encode()
                            .buildAndExpand(token.getRecipient(), rawToken)
                            .toUriString();
            String subject = verification ? "Xác minh email" : "Đặt lại mật khẩu";
            String description =
                    verification
                            ? "Bấm nút bên dưới để xác minh email của bạn. Liên kết có thời hạn và"
                                    + " chỉ dùng một lần."
                            : "Bấm nút bên dưới để đặt mật khẩu mới. Liên kết có thời hạn và chỉ"
                                    + " dùng một lần.";
            emailService.sendLink(token.getRecipient(), subject, description, link);
            token.markDelivered();
        } catch (RuntimeException exception) {
            token.markFailed(exception.getClass().getSimpleName());
            log.warn(
                    "Email delivery failed for reference {} (attempt {}/{}): {}",
                    token.getId(),
                    token.getDeliveryAttempts(),
                    maxAttempts,
                    exception.getClass().getSimpleName());
        }
        return true;
    }
}
