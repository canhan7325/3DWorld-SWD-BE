package com.project.w_3d_world.features.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DebtRestorationRequest(@NotBlank @Size(max=2000) String reason) {}
