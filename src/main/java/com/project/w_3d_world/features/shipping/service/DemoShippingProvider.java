package com.project.w_3d_world.features.shipping.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DemoShippingProvider implements ShippingProvider {
    @Override
    public String createTrackingNumber(String carrierName, int orderId) {
        return "DEMO-" + carrierName.toUpperCase() + "-" + orderId + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
