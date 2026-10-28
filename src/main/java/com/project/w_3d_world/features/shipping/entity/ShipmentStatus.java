package com.project.w_3d_world.features.shipping.entity;

/**
 * Shipment lifecycle. Seller owns only PREPARING and HANDED_TO_CARRIER;
 * transport and delivery statuses are staff controlled.
 */
public enum ShipmentStatus {
    PREPARING,
    HANDED_TO_CARRIER,
    IN_TRANSIT,
    ARRIVED_AT_DESTINATION,
    DELIVERED,
    RECEIVED,
    RETURNED,
    FAILED,
    CANCELLED,
    // Legacy values kept readable for rows created before the enum workflow.
    PENDING,
    SHIPPED
}
