package com.project.w_3d_world.features.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "print_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_request_id")
    private Integer printRequestId;

    @Column(name = "customer_user_id", nullable = false)
    private Integer customerUserId;

    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;

    @Column(name = "shipping_address_snapshot", columnDefinition = "text")
    private String shippingAddressSnapshot;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "customer_notes", columnDefinition = "text")
    private String customerNotes;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
