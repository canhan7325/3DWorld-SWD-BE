package com.project.w_3d_world.features.storefront.repository;

import com.project.w_3d_world.features.storefront.entity.Storefront;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorefrontRepository extends JpaRepository<Storefront, Integer> {
    Optional<Storefront> findByOwnerUserId(Integer ownerUserId);
}
