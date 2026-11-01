package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.features.wallet.repository.SellerEarningRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.EarningReleaseService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EarningReleaseServiceImpl implements EarningReleaseService {
    private final SellerEarningRepository earnings;
    private final CommissionWalletService commission;

    @Override
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public void releaseDue() {
        for (int orderId : earnings.findDueOrderIds(Instant.now())) {
            try {
                // Separate proxied call: each order commits/rolls back independently.
                commission.releaseEligible(orderId);
            } catch (RuntimeException exception) {
                log.warn("Seller earning release failed for order {}; will retry on next scan",orderId,exception);
            }
        }
    }
}
