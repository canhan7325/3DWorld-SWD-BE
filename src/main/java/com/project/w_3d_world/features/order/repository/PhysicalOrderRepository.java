package com.project.w_3d_world.features.order.repository;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalOrderRepository extends JpaRepository<PhysicalOrder, Integer> {
    List<PhysicalOrder> findByBuyerUserIdOrderByCreatedAtDesc(Integer userId);
    List<PhysicalOrder> findByStorefrontIdOrderByCreatedAtDesc(Integer storefrontId);
    List<PhysicalOrder> findByStatusAndCreatedAtBefore(String status, Instant before);
}
