package com.project.w_3d_world.features.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record EarningResponse(int orderId,BigDecimal grossAmount,BigDecimal commissionRate,BigDecimal commissionAmount,BigDecimal netAmount,String status,Instant eligibleAt,Instant releasedAt){}
