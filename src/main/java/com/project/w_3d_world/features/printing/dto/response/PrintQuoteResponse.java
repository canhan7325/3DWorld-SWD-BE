package com.project.w_3d_world.features.printing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PrintQuoteResponse(
        Integer printQuoteId,
        Integer printRequestId,
        Integer versionNumber,
        Integer fileVersionSnapshot,
        Integer specificationVersionSnapshot,
        BigDecimal totalAmount,
        BigDecimal depositAmount,
        BigDecimal remainingAmount,
        Instant etaAt,
        Instant issuedAt,
        Instant expiresAt,
        Instant acceptedAt,
        String quoteStatus,
        boolean current,
        int postQuoteRevisionsUsed,
        int remainingRevisions,
        List<PrintQuoteItemResponse> items
) {}
