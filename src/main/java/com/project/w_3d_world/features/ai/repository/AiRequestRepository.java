package com.project.w_3d_world.features.ai.repository;

import com.project.w_3d_world.features.ai.entity.AiRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiRequestRepository extends JpaRepository<AiRequest, Integer> {

    Optional<AiRequest> findByAiRequestIdAndUserId(Integer aiRequestId, Integer userId);

    List<AiRequest> findByUserIdOrderByCreatedAtDescAiRequestIdDesc(Integer userId);

    Page<AiRequest> findByUserIdOrderByCreatedAtDescAiRequestIdDesc(Integer userId, Pageable pageable);

    List<AiRequest> findByUserIdAndStatusOrderByCreatedAtDesc(Integer userId, String status);

    Page<AiRequest> findByUserIdAndStatusOrderByCreatedAtDesc(Integer userId, String status, Pageable pageable);

    List<AiRequest> findByUserIdAndInputImageStorageKeyIsNotNullOrderByCreatedAtDesc(Integer userId);

    List<AiRequest> findByModelIdOrderByCreatedAtDesc(Integer modelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AiRequest a where a.aiRequestId = :id and a.userId = :userId")
    Optional<AiRequest> lockByIdAndUserId(@Param("id") Integer id, @Param("userId") Integer userId);
}
