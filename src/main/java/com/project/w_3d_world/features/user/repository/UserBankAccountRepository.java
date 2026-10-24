package com.project.w_3d_world.features.user.repository;

import com.project.w_3d_world.features.user.entity.UserBankAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBankAccountRepository extends JpaRepository<UserBankAccount, Integer> {
    List<UserBankAccount> findByUserIdAndActiveTrueOrderByIdAsc(Integer userId);
    @Query("select b.userId from UserBankAccount b where b.id=:id")
    Optional<Integer> ownerId(@Param("id") int id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from UserBankAccount b where b.id=:id")
    Optional<UserBankAccount> lockById(@Param("id") int id);

    org.springframework.data.domain.Page<UserBankAccount> findByVerificationStatusAndActiveTrue(
            String status, org.springframework.data.domain.Pageable pageable);
}
