package com.project.w_3d_world.features.printing.repository;

import com.project.w_3d_world.features.printing.entity.PrintMaterial;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintMaterialRepository extends JpaRepository<PrintMaterial, Integer> {
    Page<PrintMaterial> findByIsActiveTrue(Pageable pageable);

    Page<PrintMaterial> findByIsActiveTrueAndTechnologyCode(
            String technologyCode, Pageable pageable);

    Page<PrintMaterial> findByTechnologyCode(String technologyCode, Pageable pageable);

    Page<PrintMaterial> findByIsActive(Boolean isActive, Pageable pageable);

    Page<PrintMaterial> findByTechnologyCodeAndIsActive(
            String technologyCode, Boolean isActive, Pageable pageable);
}
