package com.project.w_3d_world.features.shipping.dto;

import jakarta.validation.constraints.NotBlank;

public final class ShipmentRequests {
    private ShipmentRequests() {}

    public record CreateShipmentRequest(@NotBlank String carrierName) {}

    public record EventRequest(@NotBlank String event) {}
}
