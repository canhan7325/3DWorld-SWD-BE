package com.project.w_3d_world.features.user.repository;

import com.project.w_3d_world.features.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    @Query("select u from User u where lower(trim(u.email)) = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);

    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(trim(u.email)) = :email")
    Optional<User> lockByNormalizedEmail(@Param("email") String email);
    @Query(value = """
            SELECT * FROM users
            WHERE COALESCE(seller_verification_state->>'status','NOT_SUBMITTED') = :status
              AND account_status='ACTIVE' ORDER BY user_id DESC
            """, countQuery = """
            SELECT count(*) FROM users
            WHERE COALESCE(seller_verification_state->>'status','NOT_SUBMITTED') = :status
              AND account_status='ACTIVE'
            """, nativeQuery = true)
    org.springframework.data.domain.Page<User> findSellerVerificationQueue(
            @Param("status") String status,
            org.springframework.data.domain.Pageable pageable);
}
