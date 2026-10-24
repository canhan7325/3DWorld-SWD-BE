package com.project.w_3d_world.features.storefront.dto;

import com.project.w_3d_world.features.storefront.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class StorefrontDtos {
    private StorefrontDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 255) String name, @Size(max = 10000) String description) {}
    public record UpdateRequest(@Size(min = 1, max = 255) String name, @Size(max = 10000) String description) {}
    public record ShopResponse(int id, String name, String description, String status,
                               String verificationStatus, Instant expiresAt, boolean available) {
        public static ShopResponse from(Storefront store) {
            return new ShopResponse(store.getId(), store.getName(), store.getDescription(),
                    store.getStatus().name(), store.getVerificationStatus().name(), store.getExpiresAt(),
                    store.canPublish());
        }
    }
    public record SubscriptionResponse(Integer id, int storefrontId, BigDecimal fee, String status,
                                       Instant startAt, Instant endAt, Instant paidAt) {
        public static SubscriptionResponse from(StorefrontSubscription subscription) {
            return new SubscriptionResponse(subscription.getId(), subscription.getStorefrontId(),
                    subscription.getFeeSnapshot(), subscription.getStatus(), subscription.getStartAt(),
                    subscription.getEndAt(), subscription.getPaidAt());
        }
    }
    public record SubscriptionOverview(BigDecimal monthlyFee, Instant storefrontExpiresAt,
                                       SubscriptionResponse latest, SubscriptionResponse pending) {}
}
