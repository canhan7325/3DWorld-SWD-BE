package com.project.w_3d_world.features.wallet.repository;

import com.project.w_3d_world.features.wallet.dto.response.PayoutHistoryResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletLedgerResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletHistoryRepository {
    private final JdbcTemplate jdbc;

    public List<PayoutHistoryResponse> payouts(int userId, int page, int size) {
        return jdbc.query("""
                SELECT t.transaction_id,t.amount,t.status,t.payee_bank_account_id,
                  b.bank_name,b.account_number_masked,b.account_holder,
                  t.active_key,t.created_at,t.refund_reason
                FROM transactions t LEFT JOIN user_bank_accounts b
                  ON b.bank_account_id=t.payee_bank_account_id AND b.user_id=t.payee_user_id
                WHERE t.transaction_type='PAYOUT' AND t.payee_user_id=?
                ORDER BY t.created_at DESC,t.transaction_id DESC LIMIT ? OFFSET ?
                """, (rs,n)->new PayoutHistoryResponse(rs.getInt("transaction_id"),rs.getBigDecimal("amount"),
                rs.getString("status"),rs.getObject("payee_bank_account_id",Integer.class),rs.getString("bank_name"),
                rs.getString("account_number_masked"),rs.getString("account_holder"),rs.getString("active_key"),
                instant(rs,"created_at"),rs.getString("refund_reason")),userId,size,(long)page*size);
    }
    public List<WalletLedgerResponse> ledgers(int userId, int page, int size) {
        return jdbc.query("""
                SELECT l.ledger_id,l.entry_type,l.balance_bucket,l.signed_amount,l.balance_after,
                  l.transaction_id,l.seller_earning_id,e.physical_order_id,l.created_at
                FROM wallet_ledgers l JOIN wallets w ON w.wallet_id=l.wallet_id
                LEFT JOIN seller_earnings e ON e.earning_id=l.seller_earning_id
                WHERE w.user_id=? ORDER BY l.created_at DESC,l.ledger_id DESC LIMIT ? OFFSET ?
                """, (rs,n)->new WalletLedgerResponse(rs.getInt("ledger_id"),rs.getString("entry_type"),
                rs.getString("balance_bucket"),rs.getBigDecimal("signed_amount"),rs.getBigDecimal("balance_after"),
                rs.getObject("transaction_id",Integer.class),rs.getObject("seller_earning_id",Integer.class),
                rs.getObject("physical_order_id",Integer.class),instant(rs,"created_at")),userId,size,(long)page*size);
    }
    private Instant instant(ResultSet rs,String field) throws SQLException {
        var value=rs.getTimestamp(field);return value==null?null:value.toInstant();
    }
}
