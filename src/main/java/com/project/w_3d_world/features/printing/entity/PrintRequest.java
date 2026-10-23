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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "print_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_request_id")
    private Integer printRequestId;

    @Column(name = "customer_user_id", nullable = false)
    private Integer customerUserId;

    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address_snapshot", columnDefinition = "jsonb")
    private String shippingAddressSnapshot;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "customer_notes", columnDefinition = "text")
    private String customerNotes;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "current_file_version")
    private Integer currentFileVersion;

    @Column(name = "current_quote_id")
    private Integer currentQuoteId;

    @Column(name = "current_spec_version")
    private Integer currentSpecVersion;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "handled_by_user_id")
    private Integer handledByUserId;

    @Column(name = "copyright_confirmed", nullable = false)
    private boolean copyrightConfirmed;

    @Column(name = "preview_confirmed", nullable = false)
    private boolean previewConfirmed;

    public PrintRequest(Integer customerUserId, String customerNotes) {
        this.customerUserId = customerUserId;
        this.customerNotes = customerNotes;
        this.status = "DRAFT";
        this.createdAt = Instant.now();
    }
}
