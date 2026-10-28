package com.project.w_3d_world.features.shipping.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.entity.OrderStatusHistory;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.OrderStatusHistoryRepository;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.order.service.OrderService;
import com.project.w_3d_world.features.shipping.dto.ShipmentRequests.CreateShipmentRequest;
import com.project.w_3d_world.features.shipping.dto.ShipmentResponses.ShipmentResponse;
import com.project.w_3d_world.features.shipping.entity.Shipment;
import com.project.w_3d_world.features.shipping.repository.ShipmentRepository;
import com.project.w_3d_world.features.shipping.service.ShipmentService;
import com.project.w_3d_world.features.shipping.service.ShippingProvider;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository shipments;
    private final PhysicalOrderRepository orders;
    private final StorefrontRepository storefronts;
    private final ShippingProvider provider;
    private final OrderService orderService;
    private final AccountAuthorization authorization;
    private final OrderStatusHistoryRepository history;
    private final AuditLogRepository audit;
    private final EntityManager entityManager;
    private final com.project.w_3d_world.features.wallet.service.CommissionWalletService wallet;

    @Override
    @Transactional
    public ShipmentResponse create(int sellerId, int orderId, CreateShipmentRequest request) {
        PhysicalOrder order = orders.lockById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order does not exist."));
        sellerOrder(sellerId, orderId);
        if (!"PROCESSING".equals(order.getStatus()) || !"CONSUMED".equals(order.getReservationStatus())) {
            throw ApiException.invalidRequest("Order is not ready for shipment.");
        }
        if (shipments.findByPhysicalOrderId(orderId).isPresent()) {
            throw ApiException.invalidRequest("A shipment already exists for this order.");
        }
        Shipment shipment = new Shipment(
                orderId,
                request.carrierName().trim(),
                provider.createTrackingNumber(request.carrierName().trim(), orderId));
        shipment.markShipped();
        order.transition("SHIPPED");
        shipments.saveAndFlush(shipment);
        history.save(new OrderStatusHistory(
                orderId, "PROCESSING", "SHIPPED", sellerId, "Demo shipment " + shipment.getTrackingNumber()));
        audit.save(new AuditLog(
                sellerId, "SHIPMENT_CREATED", "SHIPMENT", shipment.getId(),
                "Demo shipment created", shipment.getTrackingNumber()));
        return ShipmentResponse.from(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentResponse> listForBuyer(int userId, int orderId) {
        orderService.detail(userId, orderId);
        return shipments.findByPhysicalOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(ShipmentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentResponse> listForSeller(int sellerId, int orderId) {
        sellerOrder(sellerId, orderId);
        return shipments.findByPhysicalOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(ShipmentResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ShipmentResponse applyEvent(int actorId, int shipmentId, String event) {
        Shipment shipment = shipments.findById(shipmentId)
                .orElseThrow(() -> ApiException.invalidRequest("Shipment does not exist."));
        PhysicalOrder order = orders.lockById(shipment.getPhysicalOrderId())
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        sellerOrder(actorId, order.getId());
        entityManager.refresh(shipment);
        String normalized = event.toUpperCase(java.util.Locale.ROOT);
        if (normalized.equals(shipment.getStatus())) {
            return ShipmentResponse.from(shipment);
        }
        if (("DELIVERED".equals(normalized) || "FAILED".equals(normalized))
                && !("SHIPPED".equals(shipment.getStatus()) || "FAILED".equals(shipment.getStatus()))) {
            throw ApiException.invalidRequest("Shipment cannot receive this event in its current state.");
        }
        if ("RETURNED".equals(normalized)
                && !("SHIPPED".equals(shipment.getStatus()) || "FAILED".equals(shipment.getStatus())
                     || "DELIVERED".equals(shipment.getStatus()))) {
            throw ApiException.invalidRequest("Shipment cannot be returned in its current state.");
        }
        if (("DELIVERED".equals(normalized) && !"SHIPPED".equals(order.getStatus()))
                || ("RETURNED".equals(normalized) && !("SHIPPED".equals(order.getStatus())
                    || "DELIVERED".equals(order.getStatus())))) {
            throw ApiException.invalidRequest("Order cannot receive this shipping event in its current state.");
        }
        String from = order.getStatus();
        switch (normalized) {
            case "DELIVERED" -> {
                shipment.markDelivered();
                order.transition("DELIVERED");
                order.deliveredPolicy();
            }
            case "FAILED" -> shipment.markFailed();
            case "RETURNED" -> {
                shipment.markReturned();
                order.transition("RETURNED");
                wallet.returned(order.getId(), order.getShippingFee());
            }
            default -> throw ApiException.invalidRequest("Unsupported shipment event.");
        }
        if (!from.equals(order.getStatus())) {
            history.save(new OrderStatusHistory(
                    order.getId(), from, order.getStatus(), actorId, "Shipment " + shipment.getTrackingNumber()));
        }
        audit.save(new AuditLog(
                actorId, "SHIPMENT_EVENT", "SHIPMENT", shipmentId, normalized, shipment.getTrackingNumber()));
        return ShipmentResponse.from(shipment);
    }

    private PhysicalOrder sellerOrder(int sellerId, int orderId) {
        PhysicalOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        boolean owner = storefronts.findByOwnerUserId(sellerId)
                .filter(storefront -> storefront.getId().equals(order.getStorefrontId())).isPresent();
        if (!owner) {
            authorization.requireRole(sellerId, RoleCode.ADMIN,
                    RoleCode.PRINT_STAFF);
        }
        return order;
    }
}
