package com.project.w_3d_world.features.payment.service;

import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PaymentResponse;

public interface PaymentService {
    PaymentResponse create(int userId, CreatePaymentRequest request);
    void callback(String data, String mac);
}
