package com.project.w_3d_world.features.audit.repository;

import com.project.w_3d_world.features.audit.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Integer entityId, org.springframework.data.domain.Pageable pageable);
}
