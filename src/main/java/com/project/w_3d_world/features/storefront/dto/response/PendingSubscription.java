package com.project.w_3d_world.features.storefront.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PendingSubscription(int subscriptionId, BigDecimal fee, Instant createdAt) {}
