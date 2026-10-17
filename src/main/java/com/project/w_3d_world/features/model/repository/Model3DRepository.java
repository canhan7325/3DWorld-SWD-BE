package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.Model3D;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Model3DRepository extends JpaRepository<Model3D, Integer> {
    Page<Model3D> findByOwnerUserIdAndDeletedAtIsNull(Integer ownerUserId, Pageable pageable);

    Optional<Model3D> findByModelIdAndOwnerUserIdAndDeletedAtIsNull(Integer modelId, Integer ownerUserId);
}
