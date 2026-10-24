package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record PrintRequestStateResponse(
        Integer printRequestId, String status, boolean copyrightConfirmed,
        boolean previewConfirmed, Integer currentFileVersion,
        Integer currentSpecVersion, Instant submittedAt, Instant cancelledAt,
        Instant updatedAt) {}
