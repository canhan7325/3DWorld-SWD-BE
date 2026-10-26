package com.project.w_3d_world.features.payment.service;

import com.project.w_3d_world.features.payment.dto.PaymentRequests.CreatePaymentRequest;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PaymentResponse;
import com.project.w_3d_world.features.payment.dto.PaymentResponses.PodPaymentStatusResponse;
import java.math.BigDecimal;

public interface PaymentService {
    PaymentResponse create(int userId, CreatePaymentRequest request);
    PodPaymentStatusResponse getPodStatus(int userId, int printRequestId);
    void callback(String data, String mac);

    void reconcilePending();

    PaymentResponse refund(int userId, int transactionId, BigDecimal amount, String reason);
}
