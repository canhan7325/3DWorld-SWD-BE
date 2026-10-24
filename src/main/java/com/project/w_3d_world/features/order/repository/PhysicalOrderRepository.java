package com.project.w_3d_world.features.order.repository;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalOrderRepository extends JpaRepository<PhysicalOrder, Integer> {
    List<PhysicalOrder> findByBuyerUserIdOrderByCreatedAtDesc(Integer userId);
    List<PhysicalOrder> findByStorefrontIdOrderByCreatedAtDesc(Integer storefrontId);
    List<PhysicalOrder> findByStatusAndCreatedAtBefore(String status, Instant before);
    List<PhysicalOrder> findByBuyerUserIdAndIdempotencyKeyOrderByIdAsc(int userId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PhysicalOrder o where o.id = :id")
    Optional<PhysicalOrder> lockById(@Param("id") int id);

    @Query("""
            select o.id from PhysicalOrder o
            where o.reservationStatus = 'RESERVED' and o.reservationExpiresAt <= :now
              and o.status in ('CREATED', 'PENDING_PAYMENT') order by o.id
            """)
    List<Integer> findExpiredReservationIds(@Param("now") Instant now);
}
