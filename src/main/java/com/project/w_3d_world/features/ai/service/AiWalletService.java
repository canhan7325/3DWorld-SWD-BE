package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.features.ai.dto.request.AiTopupRequest;
import com.project.w_3d_world.features.ai.dto.response.AiWalletResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;

public interface AiWalletService {

    AiWalletResponse wallet(int userId);

    AiWalletResponse topup(int userId, AiTopupRequest request);

    void checkSufficientCredits(int userId, int creditCost);

    void deductCredits(int userId, int creditCost, String reason);

    int refundAiCreditsOnce(AiRequest aiRequest, int userId, String auditReason);
}
