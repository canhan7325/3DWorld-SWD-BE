package com.project.w_3d_world.features.wallet.repository;

import com.project.w_3d_world.features.wallet.dto.response.PayoutHistoryResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletLedgerResponse;
import java.util.List;

public interface WalletHistoryRepository {
    List<PayoutHistoryResponse> payouts(int userId, int page, int size);
    List<WalletLedgerResponse> ledgers(int userId, int page, int size);
}
