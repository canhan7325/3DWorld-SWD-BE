package com.project.w_3d_world.features.shipping.service.impl;

import com.project.w_3d_world.features.shipping.service.ShipmentAutoCreationService;
import com.project.w_3d_world.features.shipping.service.ShippingProvider;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.shipping.entity.Shipment;
import com.project.w_3d_world.features.shipping.entity.ShipmentStatus;
import com.project.w_3d_world.features.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentAutoCreationServiceImpl implements ShipmentAutoCreationService {
    private final ShipmentRepository shipments;
    private final ShippingProvider provider;
    private final AuditLogRepository audit;

    @Transactional
    public Shipment createForOrder(PhysicalOrder order) {
        return shipments.findByPhysicalOrderId(order.getId()).orElseGet(() -> {
            Shipment shipment = new Shipment(order.getId(), "GHTK", provider.createTrackingNumber("GHTK", order.getId()));
            shipment.markPreparing();
            Shipment saved = shipments.saveAndFlush(shipment);
            audit.save(new AuditLog(order.getBuyerUserId(), "SHIPMENT_AUTO_CREATED", "SHIPMENT", saved.getId(), "Shipment created automatically at checkout", saved.getTrackingNumber()));
            return saved;
        });
    }
}
