package com.project.w_3d_world.features.wallet.repository;

import com.project.w_3d_world.features.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    Optional<Wallet> findByUserId(Integer userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId=:userId")
    Optional<Wallet> lockByUserId(@Param("userId") int userId);
}
