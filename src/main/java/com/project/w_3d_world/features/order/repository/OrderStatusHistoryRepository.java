package com.project.w_3d_world.features.order.repository;

import com.project.w_3d_world.features.order.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Integer> {
    List<OrderStatusHistory> findByOrderIdOrderByChangedAtAscIdAsc(int orderId);
}
