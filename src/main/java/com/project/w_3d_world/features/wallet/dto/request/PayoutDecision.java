package com.project.w_3d_world.features.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PayoutDecision(@NotNull Boolean approved,@NotBlank @Size(max=1000) String reason,@Size(max=255) String transferReference){}
