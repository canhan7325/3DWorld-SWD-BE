package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.ModelVersion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Integer> {
    Page<ModelVersion> findByModelIdOrderByVersionNumberDesc(Integer modelId, Pageable pageable);

    List<ModelVersion> findByModelIdOrderByVersionNumberDesc(Integer modelId);

    Optional<ModelVersion> findTopByModelIdOrderByVersionNumberDesc(Integer modelId);
    /** Phiên bản mới nhất của nhiều mô hình trong một truy vấn (tránh N+1 khi liệt kê dự án). */
    @Query("select v from ModelVersion v where v.modelId in :modelIds and v.versionNumber = "
            + "(select max(v2.versionNumber) from ModelVersion v2 where v2.modelId = v.modelId)")
    List<ModelVersion> findLatestByModelIdIn(@Param("modelIds") Collection<Integer> modelIds);

    Optional<ModelVersion> findByModelIdAndVersionNumber(Integer modelId, Integer versionNumber);

    Optional<ModelVersion> findByModelVersionIdAndModelId(Integer modelVersionId, Integer modelId);

    boolean existsByModelIdAndVersionNumber(Integer modelId, Integer versionNumber);

    Optional<ModelVersion> findByAiRequestId(Integer aiRequestId);
}
