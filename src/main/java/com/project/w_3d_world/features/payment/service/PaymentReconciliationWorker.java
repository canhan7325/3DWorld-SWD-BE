package com.project.w_3d_world.features.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationWorker {
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payment.reconciliation-interval-ms:60000}")
    public void reconcile() {
        try {
            paymentService.reconcilePending();
        } catch (RuntimeException exception) {
            log.warn("Payment reconciliation failed", exception);
        }
    }
}
