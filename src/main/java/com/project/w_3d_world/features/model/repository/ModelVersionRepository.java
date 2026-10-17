package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.ModelVersion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Integer> {
    Page<ModelVersion> findByModelIdOrderByVersionNumberDesc(Integer modelId, Pageable pageable);

    Optional<ModelVersion> findByModelVersionIdAndModelId(Integer modelVersionId, Integer modelId);

    boolean existsByModelIdAndVersionNumber(Integer modelId, Integer versionNumber);
}
