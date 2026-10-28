package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.features.shipping.dto.ShipmentRequests.CreateShipmentRequest;
import com.project.w_3d_world.features.shipping.dto.ShipmentResponses.ShipmentResponse;
import java.util.List;

public interface ShipmentService {
    ShipmentResponse create(int sellerId, int orderId, CreateShipmentRequest request);

    List<ShipmentResponse> listForBuyer(int userId, int orderId);

    List<ShipmentResponse> listForSeller(int sellerId, int orderId);

    List<ShipmentResponse> listForStaff(int staffId, int page, int size, String status);

    ShipmentResponse applyEvent(int actorId, int shipmentId, String event);
}
