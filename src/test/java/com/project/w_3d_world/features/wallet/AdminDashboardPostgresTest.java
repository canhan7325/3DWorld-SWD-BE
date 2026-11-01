package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.wallet.repository.AdminDashboardRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="dashboard.pg", matches="true")
class AdminDashboardPostgresTest {
    @Test void migrationAdoptsExistingSchemaAndDashboardQueriesMatchMoney() { verifyMigrationAndQueries(false); }
    @Test void migrationPreservesPreviouslyAddedDebtColumn() { verifyMigrationAndQueries(true); }
    private void verifyMigrationAndQueries(boolean existingDebt) {
        String schema="dashboard_"+java.util.UUID.randomUUID().toString().replace("-","");
        var adminData=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55449/postgres","canhan7325","");
        var admin=new JdbcTemplate(adminData);
        admin.execute("CREATE SCHEMA "+schema);
        var source=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55449/postgres?currentSchema="+schema,"canhan7325","");
        var jdbc=new JdbcTemplate(source);
        try {
            jdbc.execute("CREATE TABLE wallets(wallet_id serial primary key,user_id int,available_balance numeric,pending_balance numeric,held_balance numeric,updated_at timestamptz)");
            if(existingDebt) {
                jdbc.execute("ALTER TABLE wallets ADD COLUMN return_shipping_debt numeric NOT NULL DEFAULT 0");
                jdbc.execute("INSERT INTO wallets(user_id,return_shipping_debt,updated_at) VALUES(99,45000,'2020-01-01')");
            }
            var flyway=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .baselineOnMigrate(true).baselineVersion("0").locations("classpath:db/migration").load();
            flyway.migrate();
            flyway.migrate();
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND table_name='wallets' AND column_name='return_shipping_debt'",Integer.class,schema));
            if(existingDebt) {
                money("45000",jdbc.queryForObject("SELECT return_shipping_debt FROM wallets WHERE user_id=99",BigDecimal.class));
                assertNotNull(jdbc.queryForObject("SELECT shipping_debt_since FROM wallets WHERE user_id=99",java.sql.Timestamp.class));
                jdbc.update("DELETE FROM wallets WHERE user_id=99");
            }
            jdbc.execute("CREATE TABLE storefronts(storefront_id int,storefront_name text,owner_user_id int,status text,created_at timestamptz)");
            jdbc.execute("CREATE TABLE users(user_id int,full_name text)");
            jdbc.execute("CREATE TABLE physical_orders(physical_order_id int,order_code text,storefront_id int,total_amount numeric,order_status text,created_at timestamptz,refund_deadline_at timestamptz)");
            jdbc.execute("CREATE TABLE seller_earnings(earning_id int,physical_order_id int,gross_amount numeric,commission_amount numeric,commission_rate_snapshot numeric,net_amount numeric,earning_status text,created_at timestamptz,released_at timestamptz,release_eligible_at timestamptz)");
            jdbc.execute("CREATE TABLE transactions(physical_order_id int,transaction_type text,status text)");
            jdbc.execute("CREATE TABLE storefront_subscriptions(subscription_id int,storefront_id int,fee_snapshot numeric,paid_at timestamptz,start_at timestamptz,end_at timestamptz,status text)");
            jdbc.execute("CREATE TABLE wallet_ledgers(ledger_id int,wallet_id int,entry_type text,signed_amount numeric,seller_earning_id int,transaction_id int,idempotency_key text,created_at timestamptz)");
            jdbc.execute("CREATE TABLE audit_logs(audit_log_id int,entity_id int,entity_type text,action_type text,reason text,metadata jsonb,created_at timestamptz)");
            jdbc.execute("INSERT INTO storefronts VALUES(2,'Shop',9,'ACTIVE','2020-01-01')");
            jdbc.execute("INSERT INTO users VALUES(9,'Seller')");
            jdbc.execute("INSERT INTO wallets(user_id,available_balance,pending_balance,held_balance,return_shipping_debt,shipping_debt_since) VALUES(9,0,0,0,80000,'2020-01-01')");
            jdbc.execute("INSERT INTO physical_orders VALUES(1,'ORD-1',2,100000,'RECEIVED','2020-01-01','2020-01-04'),(2,'ORD-2',2,200000,'SHIPPED','2020-01-01',null),(3,'ORD-3',2,100000,'RETURNED','2020-01-01',null)");
            jdbc.execute("INSERT INTO seller_earnings VALUES(1,1,100000,5000,0.05,95000,'RELEASED','2020-01-01','2020-01-05','2020-01-04'),(2,2,200000,10000,0.05,190000,'PENDING','2020-01-01',null,null),(3,3,100000,0,0.05,100000,'VOID','2020-01-01',null,null)");
            jdbc.execute("INSERT INTO storefront_subscriptions VALUES(1,2,99000,'2020-01-01','2020-01-01','2020-02-01','ACTIVE')");
            jdbc.execute("INSERT INTO audit_logs VALUES(1,3,'PHYSICAL_ORDER','RETURN_SHIPPING_ASSESSED','return','{\"payer\":\"SELLER\",\"outboundFee\":40000,\"returnFee\":40000,\"debited\":0,\"debt\":80000}',now())");
            var repository=new AdminDashboardRepository(new NamedParameterJdbcTemplate(jdbc));
            var from=Instant.parse("2019-12-31T17:00:00Z");var to=Instant.parse("2020-01-31T17:00:00Z");
            var metrics=repository.metrics(from,to);
            money("104000",metrics.get("realized_revenue"));
            money("10000",metrics.get("provisional_commission"));
            money("80000",metrics.get("total_shipping_debt"));
            assertEquals(3,repository.commissions(from,to,2,0,20).size());
            assertEquals(1,repository.subscriptions(from,to,2,9,0,20).size());
            assertEquals(Boolean.TRUE,repository.wallets(true,true,30,0,20).getFirst().get("suspension_candidate"));
            assertEquals(1,repository.debtDetails(2).size());
            assertTrue(repository.debtPayments(9).isEmpty());
            money("5000",repository.shopTotals(2).get("realized_commission"));
            jdbc.execute("INSERT INTO transactions VALUES(1,'REFUND','PENDING')");
            money("99000",repository.metrics(from,to).get("realized_revenue"));
            assertTrue(repository.commissions(from,to,2,1,20).isEmpty());
        } finally {admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    private void money(String expected,Object actual){assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)actual));}
}
