package com.project.w_3d_world.features.shipping.repository;

import com.project.w_3d_world.features.shipping.entity.Shipment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Integer> {
    Optional<Shipment> findByPhysicalOrderId(Integer physicalOrderId);

    List<Shipment> findByPhysicalOrderIdOrderByCreatedAtDesc(Integer physicalOrderId);

    List<Shipment> findAllByOrderByCreatedAtDesc();

    List<Shipment> findByStatusOrderByCreatedAtDesc(com.project.w_3d_world.features.shipping.entity.ShipmentStatus status);
}
