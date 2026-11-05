package com.project.w_3d_world.features.payment.service;

import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.payment.dto.request.EvidenceRequest;
import com.project.w_3d_world.features.payment.dto.response.CodResponse;

public interface CodPaymentService {

    void create(PhysicalOrder order);
    CodResponse get(int actor, int orderId);
    void cancel(PhysicalOrder order);
    CodResponse collect(int actor, int orderId, EvidenceRequest request);
    CodResponse settle(int actor, int orderId, EvidenceRequest request);
}
