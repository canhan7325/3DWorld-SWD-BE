package com.project.w_3d_world.features.wallet.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record WalletResponse(int userId,Integer walletId,BigDecimal minimumReserve,BigDecimal withdrawableBalance,BigDecimal pendingBalance,BigDecimal availableBalance,BigDecimal payoutHold,BigDecimal returnShippingDebt,List<EarningResponse> earnings,BigDecimal reserveBalance,BigDecimal reserveShortfall){}
