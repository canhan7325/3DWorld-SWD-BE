package com.project.w_3d_world.features.storefront.dto.response;

import com.project.w_3d_world.features.storefront.entity.StorefrontSubscription;
import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionResponse(Integer id, int storefrontId, BigDecimal fee, String status,
                                       Instant startAt, Instant endAt, Instant paidAt) {
        public static SubscriptionResponse from(StorefrontSubscription subscription) {
            return new SubscriptionResponse(subscription.getId(), subscription.getStorefrontId(),
                    subscription.getFeeSnapshot(), subscription.getStatus(), subscription.getStartAt(),
                    subscription.getEndAt(), subscription.getPaidAt());
        }
    }
