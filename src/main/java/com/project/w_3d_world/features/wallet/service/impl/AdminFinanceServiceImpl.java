package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.features.wallet.service.AdminFinanceService;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AdminFinanceServiceImpl implements AdminFinanceService {
    private final JdbcTemplate jdbc;
    private final AccountAuthorization authorization;
    private static final String ORDER_FINANCE="""
            SELECT o.physical_order_id,o.order_code,o.order_status,o.total_amount,o.subtotal,o.shipping_fee,
            coalesce(p.receipts,0) AS platform_received,
            coalesce(r.refunded,0) AS buyer_refunded,
            coalesce(p.receipts,0)-coalesce(r.refunded,0) AS actual_order_receipts,
            coalesce(e.commission_amount,0) AS commission_snapshot,
            CASE WHEN e.earning_status='RELEASED' AND o.order_status='RECEIVED'
              AND coalesce(r.refunded,0)=0 THEN e.commission_amount ELSE 0 END AS realized_commission,
            e.earning_status,CASE WHEN e.earning_status='VOID' THEN 0 ELSE coalesce(e.net_amount,0) END AS seller_entitlement,
            0 AS actual_two_way_shipping, NULL AS shipping_payer, 0 AS shipping_debt
            FROM physical_orders o
            LEFT JOIN (SELECT physical_order_id,sum(amount) receipts FROM transactions
              WHERE transaction_type='PAYMENT' AND status='SUCCESS' AND settlement_mode IN ('PLATFORM','COD') GROUP BY physical_order_id) p USING(physical_order_id)
            LEFT JOIN (SELECT physical_order_id,sum(amount) refunded FROM transactions WHERE transaction_type='REFUND' AND status='SUCCESS' GROUP BY physical_order_id) r USING(physical_order_id)
            LEFT JOIN seller_earnings e USING(physical_order_id)
            """;

    public Map<String,Object> order(int actor,int id){
        authorization.requireRole(actor,RoleCode.ADMIN);
        var rows=jdbc.queryForList(ORDER_FINANCE+" WHERE o.physical_order_id=?",id);
        if(rows.isEmpty()) throw ApiException.invalidRequest("Order does not exist.");
        var result=new LinkedHashMap<>(rows.getFirst());
        result.put("shippingAssessments",jdbc.queryForList("SELECT actor_user_id,reason,metadata,created_at FROM audit_logs WHERE entity_type='PHYSICAL_ORDER' AND entity_id=? AND action_type='RETURN_SHIPPING_ASSESSED' ORDER BY audit_log_id",id));
        return result;
    }
    public List<Map<String,Object>> orders(int actor,int page,int size){
        authorization.requireRole(actor,RoleCode.ADMIN);validatePage(page,size);
        return jdbc.queryForList(ORDER_FINANCE+" ORDER BY o.physical_order_id DESC LIMIT ? OFFSET ?",size,(long)page*size);
    }
    public List<Map<String,Object>> packages(int actor){
        authorization.requireRole(actor,RoleCode.ADMIN);
        // Group by purpose/stage so additional paid package types are included without counting top-ups as income.
        return jdbc.queryForList("""
                SELECT purpose,payment_stage,count(*) AS paid_count,sum(amount) AS received_amount,
                sum(CASE WHEN fulfillment_status='COMPLETED' THEN amount ELSE 0 END) AS realized_income,
                sum(CASE WHEN fulfillment_status<>'COMPLETED' THEN amount ELSE 0 END) AS reconciliation_amount
                FROM transactions WHERE status='SUCCESS' AND transaction_type='PAYMENT'
                AND settlement_mode='PLATFORM' AND physical_order_id IS NULL AND rental_contract_id IS NULL
                AND print_request_id IS NULL AND wallet_id IS NULL
                GROUP BY purpose,payment_stage ORDER BY purpose,payment_stage
                """);
    }
    public Map<String,Object> cash(int actor){
        authorization.requireRole(actor,RoleCode.ADMIN);
        return jdbc.queryForMap("""
                SELECT p.received,r.refunded,w.paid_out,
                  p.received-r.refunded-w.paid_out AS recorded_net_cash,
                  (SELECT coalesce(sum(pending_balance+available_balance+held_balance),0) FROM wallets) AS wallet_liabilities,
                  (SELECT coalesce(sum(realized_commission),0) FROM (
                """+ORDER_FINANCE+"""
                  ) f) AS realized_order_commission
                FROM (SELECT coalesce(sum(amount),0) received FROM transactions
                  WHERE status='SUCCESS' AND transaction_type IN ('PAYMENT','TOPUP') AND settlement_mode IN ('PLATFORM','COD')) p
                CROSS JOIN (SELECT coalesce(sum(amount),0) refunded FROM transactions WHERE transaction_type='REFUND' AND status='SUCCESS') r
                CROSS JOIN (SELECT coalesce(sum(amount),0) paid_out FROM transactions WHERE status='SUCCESS' AND transaction_type='PAYOUT') w
                """);
    }
    private void validatePage(int page,int size){if(page<0 || size<1 || size>100) throw ApiException.invalidRequest("Invalid pagination.");}
}
