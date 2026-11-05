package com.project.w_3d_world.features.shipping.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveRequest(@NotEmpty @Size(max = 100) List<@Valid PackageRequest> packages) {}
