package com.project.w_3d_world.features.auth.service;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailDeliveryWorker {
    private final EmailDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${app.mail.poll-interval}")
    public void dispatch() {
        for (int i = 0; i < 10 && deliveryService.deliverNext(); i++) {
            // Each delivery commits independently, including its failure/retry state.
        }
    }
}
