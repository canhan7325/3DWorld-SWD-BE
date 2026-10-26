package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record PrintRequestSpecVersionResponse(
        Integer specVersionId,
        Integer version,
        JsonNode specificationData,
        Instant createdAt,
        Integer createdByUserId,
        boolean isCurrent) {}
