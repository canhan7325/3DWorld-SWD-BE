package com.project.w_3d_world.features.auth.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.auth.email-worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryWorker {
    private final EmailDeliveryService service;

    @Scheduled(fixedDelayString = "${app.auth.email-poll-ms:5000}")
    public void dispatch() {
        for (Integer id : service.pendingUsers()) {
            try {
                service.dispatch(id);
            } catch (RuntimeException exception) {
                log.warn("Authentication email worker could not process user {}", id);
            }
        }
    }
}
