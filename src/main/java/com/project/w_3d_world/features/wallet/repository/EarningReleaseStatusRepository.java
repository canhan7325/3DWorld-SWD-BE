package com.project.w_3d_world.features.wallet.repository;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EarningReleaseStatusRepository {
    private final JdbcTemplate jdbc;

    public List<Map<String,Object>> status(int orderId) {
        return jdbc.queryForList("""
                SELECT o.physical_order_id,o.order_status,o.total_amount,o.subtotal,o.commission_rate_snapshot AS order_commission_rate,o.received_at,o.refund_deadline_at,
                  coalesce(o.refund_deadline_at,o.received_at+make_interval(hours=>coalesce(o.refund_window_hours_snapshot,72))) AS effective_refund_deadline,
                  e.earning_id,e.earning_status,e.commission_rate_snapshot AS earning_commission_rate,e.commission_amount,e.net_amount,e.platform_funds_received_at,
                  e.release_eligible_at,e.released_at,w.pending_balance,
                  (SELECT coalesce(sum(t.amount),0) FROM transactions t
                    WHERE t.physical_order_id=o.physical_order_id AND t.transaction_type='PAYMENT'
                    AND t.status='SUCCESS' AND t.settlement_mode IN ('PLATFORM','COD')) AS platform_received,
                  EXISTS(SELECT 1 FROM transactions t WHERE t.physical_order_id=o.physical_order_id
                    AND t.transaction_type='REFUND' AND t.status IN ('PENDING','PROCESSING','SUCCESS')) AS blocking_refund
                FROM physical_orders o LEFT JOIN seller_earnings e USING(physical_order_id)
                LEFT JOIN wallets w ON w.wallet_id=e.wallet_id WHERE o.physical_order_id=?
                """,orderId);
    }
}
