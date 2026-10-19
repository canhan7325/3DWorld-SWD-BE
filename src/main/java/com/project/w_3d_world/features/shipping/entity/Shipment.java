package com.project.w_3d_world.features.shipping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Shipment(Integer physicalOrderId, String carrierName, String trackingNumber) {
        this.physicalOrderId = physicalOrderId;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
    }

    public void markShipped() {
        status = "SHIPPED";
        shippedAt = Instant.now();
    }

    public void markDelivered() {
        status = "DELIVERED";
        deliveredAt = Instant.now();
    }

    public void markFailed() {
        status = "FAILED";
    }

    public void markReturned() {
        status = "RETURNED";
    }
}
