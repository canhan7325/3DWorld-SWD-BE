package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.wallet.repository.EarningReleaseStatusRepository;
import com.project.w_3d_world.features.wallet.repository.WalletHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="wallet.history.pg",matches="true")
class WalletHistoryPostgresTest {
    @Test void historyIsScopedOrderedPaginatedAndPreservesBothLedgerBuckets(){
        String schema="wallet_history_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55449/postgres","canhan7325",""));
        admin.execute("CREATE SCHEMA "+schema);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55449/postgres?currentSchema="+schema,"canhan7325",""));
        try{
            jdbc.execute("CREATE TABLE transactions(transaction_id int,transaction_type text,payee_user_id int,payee_bank_account_id int,amount numeric,status text,active_key text,created_at timestamptz,refund_reason text,physical_order_id int,settlement_mode text)");
            jdbc.execute("CREATE TABLE user_bank_accounts(bank_account_id int,user_id int,bank_name text,account_number_masked text,account_holder text)");
            jdbc.execute("CREATE TABLE wallets(wallet_id int,user_id int,pending_balance numeric)");
            jdbc.execute("CREATE TABLE wallet_ledgers(ledger_id int,wallet_id int,entry_type text,balance_bucket text,signed_amount numeric,balance_after numeric,transaction_id int,seller_earning_id int,created_at timestamptz)");
            jdbc.execute("CREATE TABLE seller_earnings(earning_id int,physical_order_id int,earning_status text,commission_rate_snapshot numeric,commission_amount numeric,net_amount numeric,platform_funds_received_at timestamptz,release_eligible_at timestamptz,released_at timestamptz,wallet_id int)");
            jdbc.execute("CREATE TABLE physical_orders(physical_order_id int,order_status text,total_amount numeric,subtotal numeric,commission_rate_snapshot numeric,received_at timestamptz,refund_deadline_at timestamptz,refund_window_hours_snapshot int)");
            jdbc.execute("INSERT INTO user_bank_accounts VALUES(4,9,'Bank','******6789','Seller')");
            jdbc.execute("INSERT INTO transactions(transaction_id,transaction_type,payee_user_id,payee_bank_account_id,amount,status,active_key,created_at,refund_reason) VALUES(1,'PAYOUT',9,4,10000,'REQUESTED',null,'2026-11-01','Withdrawal'),(2,'PAYOUT',9,4,20000,'SUCCESS','BANK-2','2026-11-02','Withdrawal'),(3,'PAYOUT',10,4,30000,'REQUESTED',null,'2026-11-03','Other seller'),(4,'PAYMENT',9,4,40000,'SUCCESS',null,'2026-11-03','Not payout'),(5,'PAYOUT',9,99,50000,'REJECTED',null,'2026-11-02','Rejected reason')");
            jdbc.execute("INSERT INTO wallets VALUES(7,9,95000),(8,10,20000)");
            jdbc.execute("INSERT INTO wallet_ledgers VALUES(1,7,'SELLER_REVENUE_RELEASE','PENDING',-95000,0,null,4,'2026-11-02'),(2,7,'SELLER_REVENUE_RELEASE','AVAILABLE',95000,95000,null,4,'2026-11-02'),(3,8,'TOPUP','AVAILABLE',20000,20000,9,null,'2026-11-03')");
            var history=new WalletHistoryRepository(jdbc);
            var payouts=history.payouts(9,0,20);
            assertEquals(3,payouts.size());assertEquals(5,payouts.getFirst().transactionId());
            assertNull(payouts.getFirst().bankName());assertEquals("Rejected reason",payouts.getFirst().reason());
            assertEquals("BANK-2",payouts.get(1).transferReference());assertEquals("******6789",payouts.get(1).accountNumberMasked());
            assertEquals(2,history.payouts(9,1,1).getFirst().transactionId());assertTrue(history.payouts(22,0,20).isEmpty());
            jdbc.execute("INSERT INTO seller_earnings VALUES(4,37,'PENDING',0.05,5000,95000,'2026-10-25',null,null,7)");
            var ledgers=history.ledgers(9,0,20);assertEquals(2,ledgers.size());
            assertEquals("AVAILABLE",ledgers.get(0).balanceBucket());assertEquals("PENDING",ledgers.get(1).balanceBucket());
            assertEquals(37,ledgers.getFirst().orderId());assertEquals(0,ledgers.get(0).signedAmount().add(ledgers.get(1).signedAmount()).signum());
            assertEquals(1,history.ledgers(10,0,20).size());
            jdbc.execute("INSERT INTO physical_orders VALUES(37,'RECEIVED',100000,100000,0.05,'2026-10-26',null,72)");
            var statusRepo=new EarningReleaseStatusRepository(jdbc);
            var status=statusRepo.status(37).getFirst();assertNotNull(status.get("effective_refund_deadline"));
            assertEquals(0,((BigDecimal)status.get("platform_received")).signum());assertEquals(false,status.get("blocking_refund"));
            jdbc.execute("INSERT INTO transactions(transaction_id,transaction_type,physical_order_id,status,settlement_mode,amount) VALUES(9,'PAYMENT',37,'SUCCESS','COD',100000),(10,'REFUND',37,'PROCESSING','PLATFORM',100000)");
            status=statusRepo.status(37).getFirst();assertEquals(0,new BigDecimal("100000").compareTo((BigDecimal)status.get("platform_received")));assertEquals(true,status.get("blocking_refund"));
        }finally{admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}
