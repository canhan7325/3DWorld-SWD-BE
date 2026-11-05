package com.project.w_3d_world.features.order.dto.response;

import com.project.w_3d_world.features.order.entity.OrderStatusHistory;
import java.time.Instant;

public record HistoryResponse(int id, String fromStatus, String toStatus,
            Instant changedAt, Integer changedByUserId, String reason) {
        public static HistoryResponse from(OrderStatusHistory history) {
            return new HistoryResponse(history.getId(), history.getFromStatus(), history.getToStatus(),
                    history.getChangedAt(), history.getChangedByUserId(), history.getReason());
        }
    }
