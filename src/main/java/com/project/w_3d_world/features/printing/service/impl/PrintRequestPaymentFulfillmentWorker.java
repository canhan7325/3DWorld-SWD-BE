package com.project.w_3d_world.features.printing.service.impl;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrintRequestPaymentFulfillmentWorker {
    private static final int BATCH_SIZE = 100;
    private static final String CANDIDATES = """
            SELECT t.transaction_id FROM transactions t
            JOIN print_requests r ON r.print_request_id = t.print_request_id
            WHERE t.transaction_id > ? AND t.transaction_type = 'PAYMENT'
              AND t.purpose = 'POD' AND t.payment_stage = 'FULL'
              AND t.status = 'SUCCESS' AND t.fulfillment_status = 'RECONCILIATION_REQUIRED'
              AND r.status = 'QUOTE_ACCEPTED' AND r.locked_at IS NULL AND r.cancelled_at IS NULL
              AND EXISTS (SELECT 1 FROM transaction_attempts a
                  WHERE a.transaction_id = t.transaction_id AND a.provider_name = 'ZALOPAY'
                    AND a.attempt_status = 'SUCCESS' AND a.verified_at IS NOT NULL
                    AND a.provider_transaction_id IS NOT NULL
                    AND a.provider_event_snapshot IS NOT NULL)
            ORDER BY t.transaction_id LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final PrintRequestPaymentFulfillmentService fulfillment;
    private final AtomicInteger afterId = new AtomicInteger();

    @Scheduled(fixedDelayString = "${app.print.payment-fulfillment-scan-ms:60000}")
    public void reconcileVerifiedPayments() {
        List<Integer> candidates;
        try {
            candidates = jdbc.queryForList(CANDIDATES, Integer.class, afterId.get(), BATCH_SIZE);
        } catch (RuntimeException exception) {
            log.warn("POD payment fulfillment scan failed errorType={}",
                    exception.getClass().getSimpleName());
            return;
        }
        if (candidates.isEmpty()) {
            afterId.set(0);
            return;
        }
        for (Integer transactionId : candidates) {
            afterId.set(transactionId);
            try {
                fulfillment.handleVerifiedSuccess(transactionId);
            } catch (RuntimeException exception) {
                log.warn("POD payment fulfillment deferred transactionId={} errorType={}",
                        transactionId, exception.getClass().getSimpleName());
            }
        }
        if (candidates.size() < BATCH_SIZE) afterId.set(0);
    }
}
