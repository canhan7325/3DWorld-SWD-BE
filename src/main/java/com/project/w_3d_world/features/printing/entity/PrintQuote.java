package com.project.w_3d_world.features.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "print_quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintQuote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_quote_id", nullable = false)
    private Integer printQuoteId;

    @Column(name = "print_request_id", nullable = false)
    private Integer printRequestId;

    @Column(name = "quoted_by_user_id", nullable = false)
    private Integer quotedByUserId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "file_version_snapshot")
    private Integer fileVersionSnapshot;

    @Column(name = "specification_version_snapshot")
    private Integer specificationVersionSnapshot;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "remaining_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "eta_at")
    private Instant etaAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "quote_status", nullable = false, length = 30)
    private String quoteStatus;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public static PrintQuote issueUpfront(Integer printRequestId, Integer quotedByUserId,
            int versionNumber, int fileVersionSnapshot, int specificationVersionSnapshot,
            BigDecimal totalAmount, Instant etaAt, Instant issuedAt, Instant expiresAt) {
        if (printRequestId == null || printRequestId <= 0
                || quotedByUserId == null || quotedByUserId <= 0
                || versionNumber <= 0 || fileVersionSnapshot <= 0
                || specificationVersionSnapshot <= 0) {
            throw new IllegalArgumentException("Print Quote identifiers and versions must be positive.");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Print Quote total amount must be positive.");
        }
        if (issuedAt == null || expiresAt == null || !issuedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Print Quote expiresAt must be after issuedAt.");
        }
        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.UNNECESSARY);
        PrintQuote quote = new PrintQuote();
        quote.printRequestId = printRequestId;
        quote.quotedByUserId = quotedByUserId;
        quote.versionNumber = versionNumber;
        quote.fileVersionSnapshot = fileVersionSnapshot;
        quote.specificationVersionSnapshot = specificationVersionSnapshot;
        quote.totalAmount = normalizedTotal;
        quote.depositAmount = normalizedTotal;
        quote.remainingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        quote.etaAt = etaAt;
        quote.issuedAt = issuedAt;
        quote.expiresAt = expiresAt;
        quote.quoteStatus = "ISSUED";
        return quote;
    }

    public void accept(Instant acceptedAt) {
        if (!"ISSUED".equals(this.quoteStatus)) {
            throw new IllegalStateException("Only an ISSUED quote can be accepted.");
        }
        this.quoteStatus = "ACCEPTED";
        this.acceptedAt = acceptedAt == null ? Instant.now() : acceptedAt;
    }

    public void supersede() {
        if (!"ISSUED".equals(this.quoteStatus)) {
            throw new IllegalStateException("Only an ISSUED quote can be superseded.");
        }
        this.quoteStatus = "SUPERSEDED";
    }

    public void expire() {
        if (!"ISSUED".equals(this.quoteStatus)) {
            throw new IllegalStateException("Only an ISSUED quote can be expired.");
        }
        this.quoteStatus = "EXPIRED";
    }
}
