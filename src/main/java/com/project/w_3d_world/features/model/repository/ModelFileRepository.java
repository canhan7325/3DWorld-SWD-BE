package com.project.w_3d_world.features.model.repository;

import com.project.w_3d_world.features.model.entity.ModelFile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ModelFileRepository extends JpaRepository<ModelFile, Integer> {
    List<ModelFile> findByModelVersionIdOrderByFileIdAsc(Integer modelVersionId);

    Optional<ModelFile> findByFileIdAndModelVersionId(Integer fileId, Integer modelVersionId);

    @Query("select f from ModelFile f where f.modelVersionId = :modelVersionId and f.isPrimary = true")
    Optional<ModelFile> findPrimaryByModelVersionId(@Param("modelVersionId") Integer modelVersionId);
}
