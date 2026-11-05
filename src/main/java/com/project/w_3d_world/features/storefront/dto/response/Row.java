package com.project.w_3d_world.features.storefront.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record Row(int id, String name, String status, String verificationStatus, Instant expiresAt,
                      Instant createdAt, Owner owner, Plan plan, PendingSubscription pending,
                      long paidSubscriptions, BigDecimal totalPaid, Instant firstPaidAt) {}
