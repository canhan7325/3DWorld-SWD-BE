package com.project.w_3d_world.features.wallet.repository;

import com.project.w_3d_world.features.wallet.entity.SellerEarning;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerEarningRepository extends JpaRepository<SellerEarning, Integer> {
    Optional<SellerEarning> findByOrderId(Integer id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SellerEarning e where e.orderId=:id")
    Optional<SellerEarning> lockByOrderId(@Param("id") int id);
    @Query("select e from SellerEarning e where e.status='PENDING' and e.releaseEligibleAt<=:now")
    List<SellerEarning> findReleaseCandidates(@Param("now") Instant now);
}
