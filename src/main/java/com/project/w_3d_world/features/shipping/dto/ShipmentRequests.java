package com.project.w_3d_world.features.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ShipmentRequests {
    private ShipmentRequests() {}

    public record CreateShipmentRequest(@NotBlank @Size(max = 80) String carrierName) {}

    public record EventRequest(@NotBlank String event) {}
}
