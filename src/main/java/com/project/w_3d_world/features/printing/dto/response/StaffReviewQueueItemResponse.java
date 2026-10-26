package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record StaffReviewQueueItemResponse(
        Integer printRequestId,
        Instant submittedAt,
        String status,
        Integer assignedStaffId,
        Instant handledAt,
        Integer currentFileVersion,
        Integer currentSpecVersion,
        boolean reviewEligible,
        boolean quoteEligible,
        boolean canClaim) {}
