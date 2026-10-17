package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {
    List<ProductVariant> findByProductIdOrderByIdAsc(Integer productId);

    Optional<ProductVariant> findBySku(String sku);
}
