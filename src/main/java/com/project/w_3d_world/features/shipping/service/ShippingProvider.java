package com.project.w_3d_world.features.shipping.service;

public interface ShippingProvider {
    String createTrackingNumber(String carrierName, int orderId);
}
