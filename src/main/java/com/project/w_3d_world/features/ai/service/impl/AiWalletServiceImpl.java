package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.AiTopupRequest;
import com.project.w_3d_world.features.ai.dto.response.AiWalletResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiWalletService;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AiWalletServiceImpl implements AiWalletService {

    private final UserRepository userRepository;
    private final AiRequestRepository aiRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final Environment environment;
    private final boolean demoTopupEnabled;

    @Autowired
    public AiWalletServiceImpl(
            UserRepository userRepository,
            AiRequestRepository aiRequestRepository,
            AuditLogRepository auditLogRepository,
            Environment environment,
            @Value("${app.ai.demo-topup-enabled:true}") boolean demoTopupEnabled
    ) {
        this.userRepository = userRepository;
        this.aiRequestRepository = aiRequestRepository;
        this.auditLogRepository = auditLogRepository;
        this.environment = environment;
        this.demoTopupEnabled = demoTopupEnabled;
    }

    public AiWalletServiceImpl(
            UserRepository userRepository,
            AiRequestRepository aiRequestRepository,
            AuditLogRepository auditLogRepository,
            Environment environment
    ) {
        this(userRepository, aiRequestRepository, auditLogRepository, environment, true);
    }

    @Override
    @Transactional(readOnly = true)
    public AiWalletResponse wallet(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        List<AiRequest> tasks = aiRequestRepository.findByUserIdOrderByCreatedAtDescAiRequestIdDesc(userId);
        long successful = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
        return new AiWalletResponse(user.getAiCreditsBalance(), tasks.size(), successful);
    }

    @Override
    @Transactional
    public AiWalletResponse topup(int userId, AiTopupRequest request) {
        if (request == null || request.amount() == null || request.amount() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be a positive integer.");
        }
        boolean isProduction = environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (isProduction || !demoTopupEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Demo credit top-up is disabled in production environments.");
        }

        User user = userRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        user.addAiCredits(request.amount());
        userRepository.saveAndFlush(user);

        auditLogRepository.save(new AuditLog(
                userId, "AI_CREDITS_DEMO_TOPUP", "USER",
                userId, "Added " + request.amount() + " credits via demo topup", null
        ));

        return wallet(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public void checkSufficientCredits(int userId, int creditCost) {
        if (creditCost <= 0) return;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        if (user.getAiCreditsBalance() < creditCost) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "INSUFFICIENT_CREDITS: Required " + creditCost + ", available " + user.getAiCreditsBalance());
        }
    }

    @Override
    @Transactional
    public void deductCredits(int userId, int creditCost, String reason) {
        if (creditCost <= 0) return;
        User user = userRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        if (user.getAiCreditsBalance() < creditCost) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Insufficient AI credits: required " + creditCost + ", available " + user.getAiCreditsBalance());
        }
        user.deductAiCredits(creditCost);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional
    public int refundAiCreditsOnce(AiRequest aiRequest, int userId, String auditReason) {
        int toRefund = aiRequest.consumeCreditsForRefund();
        if (toRefund > 0) {
            userRepository.lockById(userId).ifPresent(u -> {
                u.addAiCredits(toRefund);
                userRepository.saveAndFlush(u);
                auditLogRepository.save(new AuditLog(
                        userId, "AI_CREDITS_REFUNDED", "AI_REQUEST",
                        aiRequest.getAiRequestId(),
                        "Refunded " + toRefund + " credits. Reason: " + auditReason,
                        null
                ));
            });
            aiRequestRepository.saveAndFlush(aiRequest);
            log.info("Refunded {} credits to user #{} for task #{}", toRefund, userId, aiRequest.getAiRequestId());
        }
        return toRefund;
    }
}
