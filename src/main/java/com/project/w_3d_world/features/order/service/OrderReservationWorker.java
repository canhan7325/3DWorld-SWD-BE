package com.project.w_3d_world.features.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReservationWorker {
    private final OrderService orders;

    @Scheduled(fixedDelayString = "${app.order.reservation-scan-ms:60000}")
    public void releaseExpired() {
        try {
            orders.releaseExpiredReservations();
        } catch (RuntimeException exception) {
            log.warn("Order reservation release failed", exception);
        }
    }
}
