package com.project.w_3d_world.features.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "print_request_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequestStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_status_history_id")
    private Integer printStatusHistoryId;

    @Column(name = "print_request_id", nullable = false)
    private Integer printRequestId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by_user_id")
    private Integer changedByUserId;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "source_event_id", length = 255)
    private String sourceEventId;

    public PrintRequestStatusHistory(Integer printRequestId, String fromStatus, String toStatus,
            Integer changedByUserId, String reason, String sourceEventId) {
        this.printRequestId = printRequestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.reason = reason;
        this.sourceEventId = sourceEventId;
    }
}
