package com.project.w_3d_world.features.storefront.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record Plan(String code, String name, int durationDays, String status, BigDecimal fee,
                       Integer subscriptionId, Instant startAt, Instant endAt, Instant paidAt, Long daysLeft) {}
