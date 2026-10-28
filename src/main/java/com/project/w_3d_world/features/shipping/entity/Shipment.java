package com.project.w_3d_world.features.shipping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Integer id;

    @Column(name = "physical_order_id")
    private Integer physicalOrderId;

    @Column(name = "print_request_id")
    private Integer printRequestId;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.PREPARING;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Shipment(Integer physicalOrderId, String carrierName, String trackingNumber) {
        this.physicalOrderId = physicalOrderId;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
    }

    public void markPreparing() { status = ShipmentStatus.PREPARING; updatedAt = Instant.now(); }

    public void handToCarrier() {
        status = ShipmentStatus.SHIPPED;
        updatedAt = Instant.now();
        shippedAt = Instant.now();
    }

    public void markInTransit() { status = ShipmentStatus.IN_TRANSIT; updatedAt = Instant.now(); }
    public void markArrived() { status = ShipmentStatus.ARRIVED_AT_DESTINATION; updatedAt = Instant.now(); }
    public void markReceived() { status = ShipmentStatus.RECEIVED; updatedAt = Instant.now(); }

    public void markShipped() {
        status = ShipmentStatus.SHIPPED;
        updatedAt = Instant.now();
        shippedAt = Instant.now();
    }

    public void markDelivered() {
        status = ShipmentStatus.DELIVERED;
        updatedAt = Instant.now();
        deliveredAt = Instant.now();
    }

    public void markFailed() {
        status = ShipmentStatus.FAILED;
        updatedAt = Instant.now();
    }

    public void markReturned() {
        status = ShipmentStatus.RETURNED;
        updatedAt = Instant.now();
    }
}
