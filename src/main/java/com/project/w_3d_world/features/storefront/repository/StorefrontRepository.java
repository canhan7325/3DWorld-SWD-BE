package com.project.w_3d_world.features.storefront.repository;

import com.project.w_3d_world.features.storefront.entity.Storefront;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorefrontRepository extends JpaRepository<Storefront, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Storefront s where s.id = :id")
    Optional<Storefront> lockById(@Param("id") int id);

    @EntityGraph(attributePaths = {"owner", "owner.userRoles", "owner.userRoles.role"})
    List<Storefront> findByIdIn(Collection<Integer> ids);

    Optional<Storefront> findByOwnerUserId(Integer ownerUserId);
}
