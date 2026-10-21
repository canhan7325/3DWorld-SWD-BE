package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintMaterial;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PrintMaterialRepository extends JpaRepository<PrintMaterial, Integer> {
    Page<PrintMaterial> findByIsActiveTrue(Pageable pageable);

    Page<PrintMaterial> findByIsActiveTrueAndTechnologyCode(
            String technologyCode, Pageable pageable);

    Page<PrintMaterial> findByTechnologyCode(String technologyCode, Pageable pageable);

    Page<PrintMaterial> findByIsActive(Boolean isActive, Pageable pageable);

    Page<PrintMaterial> findByTechnologyCodeAndIsActive(
            String technologyCode, Boolean isActive, Pageable pageable);

    @Query("select (count(m) > 0) from PrintMaterial m where m.isActive = true "
            + "and lower(trim(m.materialName)) = :name and upper(trim(m.technologyCode)) = :technology")
    boolean existsActiveIdentity(@Param("name") String normalizedName,
            @Param("technology") String technologyCode);

    @Query("select (count(m) > 0) from PrintMaterial m where m.isActive = true "
            + "and m.materialId <> :excludedId and lower(trim(m.materialName)) = :name "
            + "and upper(trim(m.technologyCode)) = :technology")
    boolean existsOtherActiveIdentity(@Param("excludedId") Integer excludedId,
            @Param("name") String normalizedName, @Param("technology") String technologyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PrintMaterial m where m.materialId = :id")
    Optional<PrintMaterial> findByIdForUpdate(@Param("id") Integer materialId);
}
