package com.project.w_3d_world.features.wallet.scheduler;

import com.project.w_3d_world.features.wallet.service.EarningReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SellerEarningReleaseWorker {
    private final EarningReleaseService release;

    @Scheduled(fixedDelayString="${app.wallet.earning-release-scan-ms:60000}")
    public void releaseDue() {
        try { release.releaseDue(); }
        catch (RuntimeException exception) { log.warn("Seller earning scan failed; will retry on next scan",exception); }
    }
}
