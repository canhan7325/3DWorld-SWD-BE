package com.project.w_3d_world.features.shipping.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.shipping.dto.ShipmentRequests.CreateShipmentRequest;
import com.project.w_3d_world.features.shipping.dto.ShipmentResponses.ShipmentResponse;
import com.project.w_3d_world.features.shipping.entity.Shipment;
import com.project.w_3d_world.features.shipping.repository.ShipmentRepository;
import com.project.w_3d_world.features.shipping.service.ShipmentService;
import com.project.w_3d_world.features.shipping.service.ShippingProvider;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository shipments;
    private final PhysicalOrderRepository orders;
    private final StorefrontRepository storefronts;
    private final ShippingProvider provider;

    @Override
    @Transactional
    public ShipmentResponse create(int sellerId, int orderId, CreateShipmentRequest request) {
        PhysicalOrder order = sellerOrder(sellerId, orderId);
        if (!("PAID".equals(order.getStatus()) || "PROCESSING".equals(order.getStatus()))) {
            throw ApiException.invalidRequest("Order is not ready for shipment.");
        }
        if (shipments.findByPhysicalOrderId(orderId).isPresent()) {
            throw ApiException.invalidRequest("A shipment already exists for this order.");
        }
        if ("PAID".equals(order.getStatus())) {
            order.transition("PROCESSING");
        }
        Shipment shipment = new Shipment(
                orderId,
                request.carrierName().trim(),
                provider.createTrackingNumber(request.carrierName().trim(), orderId));
        shipment.markShipped();
        order.transition("SHIPPED");
        return ShipmentResponse.from(shipments.save(shipment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentResponse> listForBuyer(int userId, int orderId) {
        PhysicalOrder order = orders.findById(orderId)
                .filter(item -> item.getBuyerUserId().equals(userId))
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        return shipments.findByPhysicalOrderIdOrderByCreatedAtDesc(order.getId()).stream()
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
        PhysicalOrder order = orders.findById(shipment.getPhysicalOrderId())
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        sellerOrder(actorId, order.getId());
        switch (event.toUpperCase()) {
            case "DELIVERED" -> {
                shipment.markDelivered();
                order.transition("DELIVERED");
            }
            case "FAILED" -> shipment.markFailed();
            case "RETURNED" -> {
                shipment.markReturned();
                order.transition("RETURNED");
            }
            default -> throw ApiException.invalidRequest("Unsupported shipment event.");
        }
        return ShipmentResponse.from(shipment);
    }

    private PhysicalOrder sellerOrder(int sellerId, int orderId) {
        PhysicalOrder order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.invalidRequest("Order does not exist."));
        storefronts.findByOwnerUserId(sellerId)
                .filter(storefront -> storefront.getId().equals(order.getStorefrontId()))
                .orElseThrow(() -> ApiException.invalidRequest("You do not own this storefront."));
        return order;
    }
}
