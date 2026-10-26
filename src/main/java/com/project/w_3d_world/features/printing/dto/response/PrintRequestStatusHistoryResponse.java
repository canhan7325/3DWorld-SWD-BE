package com.project.w_3d_world.features.printing.dto.response;

import java.time.Instant;

public record PrintRequestStatusHistoryResponse(
        Integer historyId, String fromStatus, String toStatus,
        Instant changedAt, Integer changedByUserId, String reason) {}
