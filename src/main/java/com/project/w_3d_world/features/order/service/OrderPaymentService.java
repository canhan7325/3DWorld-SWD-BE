package com.project.w_3d_world.features.order.service;

import com.project.w_3d_world.features.payment.entity.PaymentTransaction;

public interface OrderPaymentService {

    void verified(PaymentTransaction transaction);
}
