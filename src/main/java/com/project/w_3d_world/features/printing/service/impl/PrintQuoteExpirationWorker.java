package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrintQuoteExpirationWorker {
    private static final int BATCH_SIZE = 100;
    private final PrintRequestRepository requests;
    private final PrintRequestQuoteService quotes;

    @Scheduled(fixedDelayString = "${app.print.quote-expiration-scan-ms:60000}")
    public void expireOverdueQuotes() {
        int afterId = 0;
        Instant scanTime = Instant.now();
        while (true) {
            List<Integer> candidates;
            try {
                candidates = requests.findUnacceptedQuoteExpirationCandidates(
                        scanTime, afterId, PageRequest.of(0, BATCH_SIZE));
            } catch (RuntimeException exception) {
                log.warn("Print Quote expiration scan failed errorType={}",
                        exception.getClass().getSimpleName());
                return;
            }
            if (candidates.isEmpty()) return;
            for (Integer requestId : candidates) {
                afterId = requestId;
                try {
                    quotes.expireIfOverdue(requestId);
                } catch (RuntimeException exception) {
                    log.warn("Print Quote expiration failed for requestId={} errorType={}",
                            requestId, exception.getClass().getSimpleName());
                }
            }
            if (candidates.size() < BATCH_SIZE) return;
        }
    }
}
