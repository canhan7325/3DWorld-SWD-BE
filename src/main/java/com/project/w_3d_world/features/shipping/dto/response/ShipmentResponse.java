package com.project.w_3d_world.features.shipping.dto.response;

import com.project.w_3d_world.features.shipping.entity.Shipment;
import com.project.w_3d_world.features.shipping.enums.ShipmentStatus;
import java.time.Instant;

public record ShipmentResponse(
            Integer id,
            Integer physicalOrderId,
            String carrierName,
            String trackingNumber,
            ShipmentStatus status,
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
