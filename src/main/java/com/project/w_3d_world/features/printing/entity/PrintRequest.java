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

    public void confirmCopyrightDeclaration() {
        this.copyrightConfirmed = true;
    }

    public void confirmPreview() {
        this.previewConfirmed = true;
        this.updatedAt = Instant.now();
    }

    public void submit() {
        Instant now = Instant.now();
        this.status = "SUBMITTED";
        this.submittedAt = now;
        this.updatedAt = now;
    }

    public void requireRevision() {
        this.status = "REQUIRES_REVISION";
        this.currentQuoteId = null;
        this.previewConfirmed = false;
        this.updatedAt = Instant.now();
    }

    public void markQuoted(Integer quoteId, Integer staffUserId, Instant issuedAt) {
        if (quoteId == null || quoteId <= 0) {
            throw new IllegalArgumentException("Quote ID must be positive.");
        }
        Instant now = issuedAt == null ? Instant.now() : issuedAt;
        this.currentQuoteId = quoteId;
        this.status = "QUOTED";
        this.handledByUserId = staffUserId;
        this.handledAt = now;
        this.updatedAt = now;
    }

    public void markQuoteAccepted(Integer quoteId, Instant acceptedAt) {
        if (quoteId == null || !quoteId.equals(this.currentQuoteId)) {
            throw new IllegalArgumentException("Accepted quote must match current quote.");
        }
        Instant now = acceptedAt == null ? Instant.now() : acceptedAt;
        this.status = "QUOTE_ACCEPTED";
        this.updatedAt = now;
    }

    public void lockForProduction(Instant lockedAt) {
        Instant now = lockedAt == null ? Instant.now() : lockedAt;
        this.status = "IN_QUEUE";
        this.lockedAt = now;
        this.updatedAt = now;
    }

    public void cancelExpiredQuote(String reason, Instant cancelledAt) {
        Instant now = cancelledAt == null ? Instant.now() : cancelledAt;
        this.status = "CANCELLED";
        this.currentQuoteId = null;
        this.cancellationReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public void cancel(String reason) {
        Instant now = Instant.now();
        this.status = "CANCELLED";
        this.cancellationReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public void useFileVersion(int version) {
        if (version <= 0) throw new IllegalArgumentException("File version must be positive.");
        this.currentFileVersion = version;
        this.previewConfirmed = false;
        this.updatedAt = Instant.now();
    }

    public void useSpecVersion(int version) {
        if (version <= 0) throw new IllegalArgumentException("Spec version must be positive.");
        this.currentSpecVersion = version;
        this.previewConfirmed = false;
        this.updatedAt = Instant.now();
    }
}
