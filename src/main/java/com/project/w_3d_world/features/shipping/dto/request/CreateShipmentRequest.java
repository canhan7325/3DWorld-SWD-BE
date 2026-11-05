package com.project.w_3d_world.features.shipping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShipmentRequest(@NotBlank @Size(max = 80) String carrierName) {}
