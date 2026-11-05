package com.project.w_3d_world.features.shipping.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PackageRequest(@NotNull @Positive Integer packagingProfileId,
                                 @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items) {}
