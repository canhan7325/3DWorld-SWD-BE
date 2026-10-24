package com.project.w_3d_world.features.order.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_status_history_id")
    private Integer id;

    @Column(name = "physical_order_id", nullable = false)
    private Integer orderId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30, nullable = false)
    private String toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by_user_id")
    private Integer changedByUserId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "source_event_id", unique = true, length = 255)
    private String sourceEventId;

    public OrderStatusHistory(int orderId, String from, String to, Integer actorId, String reason) {
        this.orderId = orderId;
        this.fromStatus = from;
        this.toStatus = to;
        this.changedByUserId = actorId;
        this.reason = reason;
    }
}
