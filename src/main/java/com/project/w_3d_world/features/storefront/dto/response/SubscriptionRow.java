package com.project.w_3d_world.features.storefront.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionRow(int subscriptionId, BigDecimal fee, String status, Instant startAt,
                                  Instant endAt, Instant paidAt, Instant createdAt) {}
