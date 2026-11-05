package com.project.w_3d_world.features.refund.repository;

import java.math.BigDecimal;

public interface RefundReceiptRepository {
    BigDecimal receivedByPlatform(int orderId);
}
