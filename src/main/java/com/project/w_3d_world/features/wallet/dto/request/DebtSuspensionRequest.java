package com.project.w_3d_world.features.wallet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DebtSuspensionRequest(@Min(1) @Max(3650) int inactiveDays,
                                    @NotBlank @Size(max=2000) String reason) {}
