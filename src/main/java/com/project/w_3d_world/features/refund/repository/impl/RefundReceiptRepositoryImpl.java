package com.project.w_3d_world.features.refund.repository.impl;

import com.project.w_3d_world.features.refund.repository.RefundReceiptRepository;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefundReceiptRepositoryImpl implements RefundReceiptRepository {
    private final JdbcTemplate jdbc;

    @Override
    public BigDecimal receivedByPlatform(int orderId) {
        // COD remains PROCESSING until carrier settlement to the platform is confirmed.
        return jdbc.queryForObject("""
                SELECT coalesce(sum(amount),0) FROM transactions
                WHERE physical_order_id=? AND transaction_type='PAYMENT'
                  AND status='SUCCESS' AND settlement_mode IN ('PLATFORM','COD')
                """, BigDecimal.class, orderId);
    }
}
