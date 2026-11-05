package com.project.w_3d_world.features.wallet.dto.response;

import java.math.BigDecimal;

public record PayoutResponse(int transactionId,BigDecimal amount,String status,Integer bankAccountId,String transferReference){}
