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
import com.project.w_3d_world.features.shipping.entity.*;
import com.project.w_3d_world.features.shipping.repository.ShipmentRepository;
import com.project.w_3d_world.features.shipping.service.*;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository shipments; private final PhysicalOrderRepository orders;
    private final StorefrontRepository storefronts; private final ShippingProvider provider;
    private final OrderService orderService; private final AccountAuthorization authorization;
    private final OrderStatusHistoryRepository history; private final AuditLogRepository audit;
    private final EntityManager entityManager;
    private final com.project.w_3d_world.features.wallet.service.CommissionWalletService wallet;

    /** Kept for staff/admin repair of legacy orders. New customer orders auto-create a PREPARING shipment. */
    @Override @Transactional
    public ShipmentResponse create(int actorId, int orderId, CreateShipmentRequest request) {
        authorization.requireRole(actorId, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        PhysicalOrder order = orders.lockById(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order does not exist."));
        wallet.requireNoActiveRefund(orderId);
        wallet.requireNoShippingDebt(storefronts.findById(order.getStorefrontId()).orElseThrow().getOwnerUserId());
        if (!"PROCESSING".equals(order.getStatus()) || !"CONSUMED".equals(order.getReservationStatus())) throw ApiException.invalidRequest("Order is not ready for shipment.");
        Shipment shipment = shipments.findByPhysicalOrderId(orderId).orElseGet(() -> {
            Shipment value = new Shipment(orderId, request.carrierName().trim(), provider.createTrackingNumber(request.carrierName().trim(), orderId));
            value.markPreparing(); return shipments.saveAndFlush(value);
        });
        audit.save(new AuditLog(actorId, "SHIPMENT_AUTO_CREATED_REPAIRED", "SHIPMENT", shipment.getId(), "Staff repaired missing shipment", shipment.getTrackingNumber()));
        return ShipmentResponse.from(shipment);
    }

    @Override @Transactional(readOnly = true)
    public List<ShipmentResponse> listForBuyer(int userId, int orderId) { orderService.detail(userId, orderId); return shipments.findByPhysicalOrderIdOrderByCreatedAtDesc(orderId).stream().map(ShipmentResponse::from).toList(); }
    @Override @Transactional(readOnly = true)
    public List<ShipmentResponse> listForSeller(int sellerId, int orderId) { sellerOrder(sellerId, orderId); return shipments.findByPhysicalOrderIdOrderByCreatedAtDesc(orderId).stream().map(ShipmentResponse::from).toList(); }

    @Override @Transactional(readOnly = true)
    public List<ShipmentResponse> listForStaff(int staffId, int page, int size, String status) {
        authorization.requireRole(staffId, RoleCode.ADMIN, RoleCode.PRINT_STAFF);
        if (page < 0 || size < 1 || size > 100) throw ApiException.invalidRequest("Invalid pagination.");
        List<Shipment> rows;
        if (status == null || status.isBlank()) rows = shipments.findAllByOrderByCreatedAtDesc();
        else {
            try { rows = shipments.findByStatusOrderByCreatedAtDesc(ShipmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException e) { throw ApiException.invalidRequest("Unsupported shipment status."); }
        }
        int from = Math.min(page * size, rows.size());
        return rows.subList(from, Math.min(from + size, rows.size())).stream().map(ShipmentResponse::from).toList();
    }

    @Override @Transactional
    public ShipmentResponse applyEvent(int actorId, int shipmentId, String event) {
        Shipment shipment = shipments.findById(shipmentId).orElseThrow(() -> ApiException.invalidRequest("Shipment does not exist."));
        PhysicalOrder order = orders.lockById(shipment.getPhysicalOrderId()).orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        entityManager.refresh(shipment);
        ShipmentStatus next;
        try { next = ShipmentStatus.valueOf(event.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw ApiException.invalidRequest("Unsupported shipment status."); }
        boolean owner = isOwner(actorId, order);
        boolean staff = !owner && hasStaffRole(actorId);
        if (!owner && !staff) throw new ApiException(HttpStatus.FORBIDDEN, "You cannot update this shipment.");
        if (shipment.getStatus() == next) return ShipmentResponse.from(shipment);
        if (owner && !Set.of(ShipmentStatus.PREPARING, ShipmentStatus.SHIPPED).contains(next))
            throw new ApiException(HttpStatus.FORBIDDEN, "Seller may only update PREPARING or SHIPPED.");
        if (staff && (next == ShipmentStatus.PREPARING || next == ShipmentStatus.SHIPPED)) throw ApiException.invalidRequest("Seller must set SHIPPED after handing the parcel to the carrier; Staff starts at IN_TRANSIT.");
        validateTransition(shipment.getStatus(), next, owner);
        String fromOrder = order.getStatus();
        switch (next) {
            case PREPARING -> shipment.markPreparing();
            case SHIPPED -> { shipment.handToCarrier(); if ("PROCESSING".equals(order.getStatus())) order.transition("SHIPPED"); }
            case IN_TRANSIT -> shipment.markInTransit();
            case ARRIVED_AT_DESTINATION -> shipment.markArrived();
            case DELIVERED -> { shipment.markDelivered(); if ("SHIPPED".equals(order.getStatus())) { order.transition("DELIVERED"); order.deliveredPolicy(); } }
            case RECEIVED -> { shipment.markReceived(); if ("DELIVERED".equals(order.getStatus())) { order.confirmReceived(); wallet.received(order.getId(), order.getReceivedAt()); wallet.releaseEligible(order.getId()); } }
            case FAILED -> shipment.markFailed();
            case RETURNED -> { shipment.markReturned(); if ("SHIPPED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())) order.transition("RETURNED"); wallet.returned(order.getId(), order.getShippingFee()); }
            default -> throw ApiException.invalidRequest("Unsupported shipment transition.");
        }
        if (!fromOrder.equals(order.getStatus())) history.save(new OrderStatusHistory(order.getId(), fromOrder, order.getStatus(), actorId, "Shipment " + shipment.getTrackingNumber()));
        audit.save(new AuditLog(actorId, "SHIPMENT_STATUS_UPDATED", "SHIPMENT", shipmentId, next.name(), shipment.getTrackingNumber()));
        return ShipmentResponse.from(shipment);
    }

    private void validateTransition(ShipmentStatus from, ShipmentStatus to, boolean owner) {
        if (owner) {
            if (from == ShipmentStatus.PREPARING && to == ShipmentStatus.SHIPPED) return;
            throw ApiException.invalidRequest("Seller shipment status must move from PREPARING to SHIPPED.");
        }
        boolean allowed = switch (from) {
            case PREPARING -> false;
            case SHIPPED, HANDED_TO_CARRIER -> to == ShipmentStatus.IN_TRANSIT || to == ShipmentStatus.FAILED || to == ShipmentStatus.RETURNED;
            case IN_TRANSIT -> to == ShipmentStatus.ARRIVED_AT_DESTINATION || to == ShipmentStatus.FAILED || to == ShipmentStatus.RETURNED;
            case ARRIVED_AT_DESTINATION -> to == ShipmentStatus.DELIVERED || to == ShipmentStatus.RETURNED || to == ShipmentStatus.FAILED;
            case DELIVERED -> to == ShipmentStatus.RECEIVED || to == ShipmentStatus.RETURNED;
            case FAILED -> to == ShipmentStatus.IN_TRANSIT || to == ShipmentStatus.RETURNED;
            case PENDING -> to == ShipmentStatus.PREPARING || to == ShipmentStatus.SHIPPED;
            default -> false;
        };
        if (!allowed) throw ApiException.invalidRequest("Shipment cannot move from " + from + " to " + to + ".");
    }
    private boolean isOwner(int actor, PhysicalOrder order) { return storefronts.findByOwnerUserId(actor).filter(s -> s.getId().equals(order.getStorefrontId())).isPresent(); }
    private boolean hasStaffRole(int actor) { try { authorization.requireRole(actor, RoleCode.ADMIN, RoleCode.PRINT_STAFF); return true; } catch (ApiException e) { return false; } }
    private PhysicalOrder sellerOrder(int actor, int orderId) { PhysicalOrder order=orders.findById(orderId).orElseThrow(() -> ApiException.invalidRequest("Order does not exist.")); if (!isOwner(actor,order)) authorization.requireRole(actor,RoleCode.ADMIN,RoleCode.PRINT_STAFF); return order; }
}
