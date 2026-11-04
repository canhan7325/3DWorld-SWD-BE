package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.ModelFile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ModelFileRepository extends JpaRepository<ModelFile, Integer> {
    List<ModelFile> findByModelVersionIdOrderByFileIdAsc(Integer modelVersionId);
    /** Tệp của nhiều phiên bản trong một truy vấn (tránh N+1 khi đọc chi tiết dự án / snapshot). */
    List<ModelFile> findByModelVersionIdInOrderByFileIdAsc(Collection<Integer> modelVersionIds);
    @Query("select f from ModelFile f where f.modelVersionId in :modelVersionIds and f.isPrimary = true")
    List<ModelFile> findPrimaryByModelVersionIdIn(@Param("modelVersionIds") Collection<Integer> modelVersionIds);

    Optional<ModelFile> findByFileIdAndModelVersionId(Integer fileId, Integer modelVersionId);

    @Query("select f from ModelFile f where f.modelVersionId = :modelVersionId and f.isPrimary = true")
    Optional<ModelFile> findPrimaryByModelVersionId(@Param("modelVersionId") Integer modelVersionId);
}
