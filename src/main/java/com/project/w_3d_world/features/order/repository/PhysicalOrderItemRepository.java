package com.project.w_3d_world.features.order.repository;

import com.project.w_3d_world.features.order.entity.PhysicalOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalOrderItemRepository extends JpaRepository<PhysicalOrderItem, Integer> {
    List<PhysicalOrderItem> findByOrderId(Integer orderId);
}
