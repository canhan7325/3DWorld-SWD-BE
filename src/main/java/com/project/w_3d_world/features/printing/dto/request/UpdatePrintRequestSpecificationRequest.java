package com.project.w_3d_world.features.printing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdatePrintRequestSpecificationRequest(
        @NotNull @Positive Integer expectedCurrentSpecVersion,
        List<UpdatePrintRequestSpecificationItemRequest> items) {}
