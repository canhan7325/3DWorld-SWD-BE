package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface Model3DRepository extends JpaRepository<Model3D, Integer> {
    Page<Model3D> findByOwnerUserIdAndDeletedAtIsNull(Integer ownerUserId, Pageable pageable);

    Optional<Model3D> findByModelIdAndOwnerUserIdAndDeletedAtIsNull(Integer modelId, Integer ownerUserId);

    List<Model3D> findByOwnerUserIdAndModelTypeAndDeletedAtIsNullOrderByCreatedAtDesc(Integer ownerUserId, ModelType modelType);

    List<Model3D> findByOwnerUserIdAndOriginTypeAndDeletedAtIsNullOrderByCreatedAtDesc(Integer ownerUserId, ModelOriginType originType);

    Page<Model3D> findByOwnerUserIdAndModelTypeAndDeletedAtIsNull(Integer ownerUserId, ModelType modelType, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Model3D m where m.modelId = :modelId "
            + "and m.ownerUserId = :ownerUserId and m.deletedAt is null")
    Optional<Model3D> findOwnedActiveByIdForUpdate(
            @Param("modelId") Integer modelId, @Param("ownerUserId") Integer ownerUserId);
}
