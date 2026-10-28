package com.project.w_3d_world.features.shipping.dto;

import com.project.w_3d_world.features.shipping.entity.Shipment;
import java.time.Instant;

public final class ShipmentResponses {
    private ShipmentResponses() {}

    public record ShipmentResponse(
            Integer id,
            Integer physicalOrderId,
            String carrierName,
            String trackingNumber,
            com.project.w_3d_world.features.shipping.entity.ShipmentStatus status,
            Instant shippedAt,
            Instant deliveredAt,
            Instant createdAt) {
        public static ShipmentResponse from(Shipment shipment) {
            return new ShipmentResponse(
                    shipment.getId(),
                    shipment.getPhysicalOrderId(),
                    shipment.getCarrierName(),
                    shipment.getTrackingNumber(),
                    shipment.getStatus(),
                    shipment.getShippedAt(),
                    shipment.getDeliveredAt(),
                    shipment.getCreatedAt());
        }
    }
}
