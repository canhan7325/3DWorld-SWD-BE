package com.project.w_3d_world.features.storefront.dto.response;

import com.project.w_3d_world.features.storefront.entity.Storefront;
import java.time.Instant;

public record ShopResponse(int id, String name, String description, String status,
                               String verificationStatus, Instant expiresAt, boolean available) {
        public static ShopResponse from(Storefront store) {
            return new ShopResponse(store.getId(), store.getName(), store.getDescription(),
                    store.getStatus().name(), store.getVerificationStatus().name(), store.getExpiresAt(),
                    store.canPublish());
        }
    }
