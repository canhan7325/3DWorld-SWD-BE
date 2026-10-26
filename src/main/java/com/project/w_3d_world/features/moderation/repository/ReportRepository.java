package com.project.w_3d_world.features.moderation.repository;

import com.project.w_3d_world.features.moderation.entity.Report;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Integer> {
    Page<Report> findByStatus(String status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id=:id")
    Optional<Report> lockById(@Param("id") int id);
}
