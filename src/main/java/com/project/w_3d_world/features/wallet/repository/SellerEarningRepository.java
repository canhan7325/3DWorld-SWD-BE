package com.project.w_3d_world.features.wallet.repository;

import com.project.w_3d_world.features.wallet.entity.SellerEarning;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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

    @Query("""
            select e.orderId from SellerEarning e join PhysicalOrder o on o.id=e.orderId
            where e.status='PENDING' and e.fundsReceivedAt is not null and o.status='RECEIVED'
              and o.receivedAt is not null
              and (o.refundDeadlineAt<=:now or o.refundDeadlineAt is null)
            order by e.orderId
            """)
    List<Integer> findDueOrderIds(@Param("now") Instant now);

    @Query(value="""
            select exists(select 1 from transactions where physical_order_id=:id
              and transaction_type='REFUND' and status in ('PENDING','PROCESSING','SUCCESS'))
            """,nativeQuery=true)
    boolean hasBlockingRefund(@Param("id") int id);

    @Query(value="""
            select coalesce(sum(amount),0)=:total and :total>0 from transactions
            where physical_order_id=:id and transaction_type='PAYMENT'
              and status='SUCCESS' and settlement_mode in ('PLATFORM','COD')
            """,nativeQuery=true)
    boolean hasFullPlatformReceipt(@Param("id") int id,@Param("total") BigDecimal total);
}
