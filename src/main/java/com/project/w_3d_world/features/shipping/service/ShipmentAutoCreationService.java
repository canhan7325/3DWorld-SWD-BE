package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.shipping.entity.Shipment;

public interface ShipmentAutoCreationService {
    Shipment createForOrder(PhysicalOrder order);
}
