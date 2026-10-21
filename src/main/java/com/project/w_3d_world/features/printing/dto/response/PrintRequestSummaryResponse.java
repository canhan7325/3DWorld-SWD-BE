package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record PrintRequestSummaryResponse(
        Integer printRequestId,
        String status,
        String customerNotes,
        Instant createdAt,
        Instant updatedAt,
        Instant lockedAt) {}
