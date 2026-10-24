package com.project.w_3d_world.features.storefront.repository;

import com.project.w_3d_world.features.storefront.entity.StorefrontSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorefrontSubscriptionRepository extends JpaRepository<StorefrontSubscription, Integer> {
    Optional<StorefrontSubscription> findFirstByStorefrontIdAndStatusOrderByIdDesc(int storefrontId, String status);
    Optional<StorefrontSubscription> findFirstByStorefrontIdOrderByIdDesc(int storefrontId);
}
