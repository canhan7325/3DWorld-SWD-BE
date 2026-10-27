package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.ModelVersion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Integer> {
    Page<ModelVersion> findByModelIdOrderByVersionNumberDesc(Integer modelId, Pageable pageable);

    List<ModelVersion> findByModelIdOrderByVersionNumberDesc(Integer modelId);

    Optional<ModelVersion> findTopByModelIdOrderByVersionNumberDesc(Integer modelId);

    Optional<ModelVersion> findByModelIdAndVersionNumber(Integer modelId, Integer versionNumber);

    Optional<ModelVersion> findByModelVersionIdAndModelId(Integer modelVersionId, Integer modelId);

    boolean existsByModelIdAndVersionNumber(Integer modelId, Integer versionNumber);

    Optional<ModelVersion> findByAiRequestId(Integer aiRequestId);
}
