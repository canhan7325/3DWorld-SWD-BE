package com.project.w_3d_world.features.wallet.repository;

import com.project.w_3d_world.features.wallet.entity.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Integer> {
    boolean existsByIdempotencyKey(String key);
}
