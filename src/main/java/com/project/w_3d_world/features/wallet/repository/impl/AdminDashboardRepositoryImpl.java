package com.project.w_3d_world.features.wallet.repository.impl;

import com.project.w_3d_world.features.wallet.repository.AdminDashboardRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDashboardRepositoryImpl implements AdminDashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private MapSqlParameterSource period(Instant from, Instant to, Integer shop, Integer user, int page, int size) {
        return new MapSqlParameterSource().addValue("from",java.sql.Timestamp.from(from))
                .addValue("to",java.sql.Timestamp.from(to)).addValue("shop",shop,java.sql.Types.INTEGER)
                .addValue("user",user,java.sql.Types.INTEGER).addValue("limit",size).addValue("offset",(long)page*size);
    }
    private static final String NO_REFUND = """
            NOT EXISTS (SELECT 1 FROM transactions t WHERE t.physical_order_id=o.physical_order_id
              AND t.transaction_type='REFUND' AND t.status IN ('PENDING','PROCESSING','SUCCESS'))
            """;
    @Override
    public Map<String,Object> metrics(Instant from, Instant to) {
        return jdbc.queryForMap("""
                SELECT c.realized_commission,s.subscription_income,
                  c.realized_commission+s.subscription_income AS realized_revenue,
                  p.provisional_commission,w.total_shipping_debt
                FROM (SELECT coalesce(sum(e.commission_amount),0) realized_commission
                  FROM seller_earnings e JOIN physical_orders o USING(physical_order_id)
                  WHERE e.earning_status='RELEASED' AND o.order_status='RECEIVED'
                    AND e.released_at>=:from AND e.released_at<:to AND
                """+NO_REFUND+"""
                ) c CROSS JOIN (SELECT coalesce(sum(fee_snapshot),0) subscription_income
                  FROM storefront_subscriptions WHERE paid_at>=:from AND paid_at<:to) s
                CROSS JOIN (SELECT coalesce(sum(e.commission_amount),0) provisional_commission
                  FROM seller_earnings e JOIN physical_orders o USING(physical_order_id)
                  WHERE e.earning_status IN ('PENDING','HELD') AND o.order_status IN ('PAID','PROCESSING','SHIPPED','DELIVERED','RECEIVED')
                    AND e.created_at>=:from AND e.created_at<:to AND
                """+NO_REFUND+"""
                ) p CROSS JOIN (SELECT coalesce(sum(return_shipping_debt),0) total_shipping_debt FROM wallets) w
                """,period(from,to,null,null,0,1));
    }
    @Override
    public List<Map<String,Object>> commissions(Instant from, Instant to, Integer shop, int page, int size) {
        return jdbc.queryForList("""
                SELECT o.physical_order_id,o.order_code,s.storefront_id,s.storefront_name,
                  o.total_amount AS order_value,e.gross_amount,e.commission_rate_snapshot,
                  CASE WHEN e.earning_status='VOID' THEN 0 ELSE e.commission_amount END AS commission_amount,
                  CASE WHEN e.earning_status='VOID' THEN 0 ELSE e.net_amount END AS seller_net_amount,
                  coalesce((SELECT sum(-l.signed_amount) FROM wallet_ledgers l
                    WHERE l.seller_earning_id=e.earning_id AND l.entry_type='RETURN_SHIPPING_DEBT_SETTLED'),0) AS debt_offset,
                  CASE WHEN e.earning_status='VOID' OR EXISTS(SELECT 1 FROM transactions t
                    WHERE t.physical_order_id=o.physical_order_id AND t.transaction_type='REFUND' AND t.status='SUCCESS') THEN 'REFUNDED'
                    WHEN e.earning_status='RELEASED' THEN 'RECOGNIZED' ELSE 'PENDING_REFUND_WINDOW' END AS status,
                  CASE WHEN e.earning_status='RELEASED' THEN greatest(0,e.net_amount-coalesce(
                    (SELECT sum(-l.signed_amount) FROM wallet_ledgers l WHERE l.seller_earning_id=e.earning_id
                      AND l.entry_type='RETURN_SHIPPING_DEBT_SETTLED'),0)) ELSE 0 END AS credited_to_shop,
                  EXISTS(SELECT 1 FROM transactions t WHERE t.physical_order_id=o.physical_order_id
                    AND t.transaction_type='REFUND' AND t.status IN ('PENDING','PROCESSING')) AS refund_under_review,
                  e.created_at,e.release_eligible_at,e.released_at,o.refund_deadline_at
                FROM seller_earnings e JOIN physical_orders o USING(physical_order_id)
                JOIN storefronts s USING(storefront_id)
                WHERE e.created_at>=:from AND e.created_at<:to AND (:shop IS NULL OR s.storefront_id=:shop)
                ORDER BY e.created_at DESC,e.earning_id DESC LIMIT :limit OFFSET :offset
                """,period(from,to,shop,null,page,size));
    }
    @Override
    public List<Map<String,Object>> subscriptions(Instant from, Instant to, Integer shop, Integer user, int page, int size) {
        return jdbc.queryForList("""
                SELECT b.subscription_id,s.storefront_id,s.storefront_name,s.owner_user_id,u.full_name,
                  'STOREFRONT_SUBSCRIPTION' AS package_name,'30_DAYS' AS billing_cycle,
                  b.fee_snapshot AS amount,b.paid_at,b.start_at,b.end_at,b.status
                FROM storefront_subscriptions b JOIN storefronts s USING(storefront_id)
                JOIN users u ON u.user_id=s.owner_user_id
                WHERE b.paid_at>=:from AND b.paid_at<:to
                  AND (:shop IS NULL OR s.storefront_id=:shop) AND (:user IS NULL OR s.owner_user_id=:user)
                ORDER BY b.paid_at DESC,b.subscription_id DESC LIMIT :limit OFFSET :offset
                """,period(from,to,shop,user,page,size));
    }
    @Override
    public List<Map<String,Object>> wallets(boolean debtOnly, boolean lockedOnly, int inactiveDays, int page, int size) {
        return jdbc.queryForList("""
                SELECT s.storefront_id,s.storefront_name,s.owner_user_id,s.status AS shop_status,
                  100000 AS minimum_reserve,coalesce(w.available_balance,0) AS available_balance,
                  least(coalesce(w.available_balance,0),100000) AS reserve_remaining,
                  coalesce(w.pending_balance,0) AS pending_balance,coalesce(w.held_balance,0) AS payout_hold,
                  coalesce(w.return_shipping_debt,0) AS shipping_debt,
                  -coalesce(w.return_shipping_debt,0) AS signed_shipping_debt,w.shipping_debt_since,
                  CASE WHEN coalesce(w.return_shipping_debt,0)>0 THEN 'IN_DEBT' ELSE 'NORMAL' END AS wallet_status,
                  (coalesce(w.return_shipping_debt,0)>0 OR coalesce(w.available_balance,0)<100000 OR s.status='SUSPENDED') AS withdrawal_locked,
                  a.last_order_at,
                  (coalesce(w.return_shipping_debt,0)>0 AND w.shipping_debt_since<=now()-make_interval(days=>:days)
                    AND coalesce(a.last_order_at,s.created_at)<=now()-make_interval(days=>:days)) AS suspension_candidate
                FROM storefronts s LEFT JOIN wallets w ON w.user_id=s.owner_user_id
                LEFT JOIN LATERAL (SELECT max(created_at) last_order_at FROM physical_orders
                  WHERE storefront_id=s.storefront_id AND order_status<>'CANCELLED') a ON true
                WHERE (:debtOnly=false OR coalesce(w.return_shipping_debt,0)>0)
                  AND (:lockedOnly=false OR coalesce(w.return_shipping_debt,0)>0 OR coalesce(w.available_balance,0)<100000 OR s.status='SUSPENDED')
                ORDER BY coalesce(w.return_shipping_debt,0) DESC,s.storefront_id
                LIMIT :limit OFFSET :offset
                """,new MapSqlParameterSource().addValue("debtOnly",debtOnly).addValue("lockedOnly",lockedOnly)
                .addValue("days",inactiveDays).addValue("limit",size).addValue("offset",(long)page*size));
    }
    @Override
    public List<Map<String,Object>> debtDetails(int shop) {
        return jdbc.queryForList("""
                SELECT a.audit_log_id,o.physical_order_id,o.order_code,a.reason,a.created_at,
                  a.metadata->>'refundId' AS refund_id,a.metadata->>'carrierReference' AS carrier_reference,
                  coalesce((a.metadata->>'outboundFee')::numeric,(a.metadata->>'total')::numeric/2,0) AS outbound_fee,
                  coalesce((a.metadata->>'returnFee')::numeric,(a.metadata->>'total')::numeric/2,0) AS return_fee,
                  coalesce((a.metadata->>'debited')::numeric,0) AS debited,
                  coalesce((a.metadata->>'debt')::numeric,0) AS debt_assessed
                FROM audit_logs a JOIN physical_orders o ON o.physical_order_id=a.entity_id
                WHERE a.entity_type='PHYSICAL_ORDER' AND a.action_type='RETURN_SHIPPING_ASSESSED'
                  AND coalesce(a.metadata->>'payer','SELLER')='SELLER' AND o.storefront_id=:shop
                ORDER BY a.audit_log_id
                """,Map.of("shop",shop));
    }
    @Override
    public List<Map<String,Object>> debtPayments(int seller) {
        return jdbc.queryForList("""
                SELECT l.ledger_id,l.entry_type,l.signed_amount,l.created_at,l.seller_earning_id,l.transaction_id,l.idempotency_key
                FROM wallet_ledgers l JOIN wallets w USING(wallet_id)
                WHERE w.user_id=:seller AND l.entry_type='RETURN_SHIPPING_DEBT_SETTLED'
                ORDER BY l.created_at,l.ledger_id
                """,Map.of("seller",seller));
    }
    @Override
    public Map<String,Object> shopTotals(int shop) {
        return jdbc.queryForMap("""
                SELECT s.storefront_id,s.storefront_name,s.owner_user_id,
                  (SELECT coalesce(sum(fee_snapshot),0) FROM storefront_subscriptions b
                    WHERE b.storefront_id=s.storefront_id AND b.paid_at IS NOT NULL) AS subscription_income,
                  (SELECT coalesce(sum(e.commission_amount),0) FROM seller_earnings e JOIN physical_orders o USING(physical_order_id)
                    WHERE o.storefront_id=s.storefront_id AND e.earning_status='RELEASED' AND
                """+NO_REFUND+"""
                  ) AS realized_commission,
                  coalesce(w.return_shipping_debt,0) AS shipping_debt,w.shipping_debt_since
                FROM storefronts s LEFT JOIN wallets w ON w.user_id=s.owner_user_id WHERE s.storefront_id=:shop
                """,Map.of("shop",shop));
    }
}
