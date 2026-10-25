package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record PrintRequestTechnicalReviewResponse(
        Integer technicalReviewId,
        String outcome,
        Integer reviewedFileVersion,
        Integer reviewedSpecVersion,
        String reason,
        Integer reviewerUserId,
        Instant createdAt) {}
