package com.project.w_3d_world.features.order.service;

import com.project.w_3d_world.features.order.entity.OrderStatusHistory;
import com.project.w_3d_world.features.order.repository.OrderStatusHistoryRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.storefront.service.StorefrontService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderPaymentService {
    private final StorefrontService storefronts;
    private final PhysicalOrderRepository orders;
    private final OrderStatusHistoryRepository history;

    @Transactional
    public void verified(PaymentTransaction transaction) {
        if (transaction.getSubscriptionId() != null) {
            storefronts.activateVerifiedPayment(transaction);
            return;
        }
        if (transaction.getPhysicalOrderId() == null) {
            // BE2 entitlement/POD and future wallet integration are separate handlers.
            return;
        }
        var order = orders.lockById(transaction.getPhysicalOrderId()).orElseThrow();
        if ("PAID".equals(order.getStatus()) || "PROCESSING".equals(order.getStatus())
                || "SHIPPED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())
                || "RECEIVED".equals(order.getStatus())) {
            transaction.fulfillment("COMPLETED");
            return;
        }
        if (!order.hasReservation() || order.reservationExpired(Instant.now())
                || !"PENDING_PAYMENT".equals(order.getStatus())
                || order.getTotalAmount().compareTo(transaction.getAmount()) != 0) {
            transaction.fulfillment("RECONCILIATION_REQUIRED");
            return;
        }
        order.transition("PAID");
        history.save(new OrderStatusHistory(order.getId(), "PENDING_PAYMENT", "PAID",
                null, "Verified payment transaction " + transaction.getId()));
        transaction.fulfillment("COMPLETED");
    }
}
