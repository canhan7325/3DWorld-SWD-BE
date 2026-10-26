package com.project.w_3d_world.features.auth.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.auth.email-worker-enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryWorker {
    private final EmailDeliveryService service;
    private final TaskExecutor executor;

    public EmailDeliveryWorker(EmailDeliveryService service,
                               @Qualifier("authEmailExecutor") TaskExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @TransactionalEventListener
    public void queued(EmailDeliveryRequested event) {
        try {
            // A fresh thread opens its own transaction after registration/resend has committed.
            executor.execute(() -> dispatchUser(event.userId()));
        } catch (TaskRejectedException exception) {
            log.warn("Authentication email executor is busy; the durable retry worker will deliver the email.");
        }
    }

    @Scheduled(scheduler = "authEmailScheduler", fixedDelayString = "${app.auth.email-poll-ms:2000}")
    public void dispatch() {
        for (Integer id : service.pendingUsers()) {
            dispatchUser(id);
        }
    }

    private void dispatchUser(int userId) {
        try {
            service.dispatch(userId);
        } catch (RuntimeException exception) {
            log.warn("Authentication email worker could not process user {}", userId);
        }
    }
}
