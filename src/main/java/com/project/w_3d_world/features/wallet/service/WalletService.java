package com.project.w_3d_world.features.wallet.service;

import com.project.w_3d_world.features.wallet.dto.request.PayoutDecision;
import com.project.w_3d_world.features.wallet.dto.request.PayoutRequest;
import com.project.w_3d_world.features.wallet.dto.response.PayoutResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletResponse;

public interface WalletService {
    WalletResponse mine(int user);
    PayoutResponse request(int user, PayoutRequest request);
    PayoutResponse decide(int admin, int id, PayoutDecision request);
}
