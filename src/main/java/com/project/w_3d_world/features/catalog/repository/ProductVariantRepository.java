package com.project.w_3d_world.features.catalog.repository;

import com.project.w_3d_world.features.catalog.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {
    List<ProductVariant> findByProductIdInOrderByIdAsc(Collection<Integer> ids);

    List<ProductVariant> findByProductIdOrderByIdAsc(Integer productId);

    Optional<ProductVariant> findBySku(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Integer id);
}
