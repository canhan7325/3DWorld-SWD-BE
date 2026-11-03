package com.project.w_3d_world.features.wallet.service;

import com.project.w_3d_world.features.wallet.dto.response.PayoutHistoryResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletLedgerResponse;
import java.util.List;

public interface WalletHistoryService {
    List<PayoutHistoryResponse> payouts(int seller, int page, int size);
    List<WalletLedgerResponse> ledgers(int seller, int page, int size);
}
