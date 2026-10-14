package com.project.w_3d_world.features.auth.repository;

import com.project.w_3d_world.features.auth.entity.EmailToken;
import com.project.w_3d_world.features.auth.enums.DeliveryStatus;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {
    boolean existsByUser_IdAndPurposeAndCreatedAtAfter(
            int userId, TokenPurpose purpose, Instant createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select t from EmailToken t where t.tokenHash = :hash and t.user.id = :userId and"
                + " t.purpose = :purpose")
    Optional<EmailToken> lockByHash(
            @Param("hash") String hash,
            @Param("userId") int userId,
            @Param("purpose") TokenPurpose purpose);

    @Modifying
    @Query(
            "update EmailToken t set t.usedAt = :now, t.deliveryStatus = :cancelled,"
                + " t.encryptedToken = null where t.user.id = :userId and t.purpose = :purpose and"
                + " t.usedAt is null")
    void invalidateUnused(
            @Param("userId") int userId,
            @Param("purpose") TokenPurpose purpose,
            @Param("now") Instant now,
            @Param("cancelled") DeliveryStatus cancelled);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select t from EmailToken t where t.deliveryStatus in :statuses and t.deliveryAttempts"
                + " < :maxAttempts and t.nextAttemptAt <= :now order by t.createdAt")
    List<EmailToken> lockPending(
            @Param("statuses") List<DeliveryStatus> statuses,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") Instant now,
            Pageable pageable);
}
