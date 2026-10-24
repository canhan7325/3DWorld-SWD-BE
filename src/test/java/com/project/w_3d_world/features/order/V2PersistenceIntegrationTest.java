package com.project.w_3d_world.features.order;

import static org.junit.jupiter.api.Assertions.*;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.cart.dto.CartRequests.ItemRequest;
import com.project.w_3d_world.features.cart.service.CartService;
import com.project.w_3d_world.features.cart.service.impl.CartServiceImpl;
import com.project.w_3d_world.features.catalog.repository.CatalogReadRepository;
import com.project.w_3d_world.features.order.dto.OrderRequests.CheckoutRequest;
import com.project.w_3d_world.features.order.service.OrderPaymentService;
import com.project.w_3d_world.features.order.service.OrderService;
import com.project.w_3d_world.features.order.service.impl.OrderServiceImpl;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.storefront.service.StorefrontService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@EnabledIfSystemProperty(named = "v2.pg.integration", matches = "true")
@SpringBootTest(classes = V2PersistenceIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/erdv2check",
            "spring.datasource.username=canhan7325", "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
            "spring.sql.init.mode=never", "spring.jpa.open-in-view=false"
        })
@Transactional
class V2PersistenceIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.project.w_3d_world.features")
    @EnableJpaRepositories("com.project.w_3d_world.features")
    @Import({CartServiceImpl.class, OrderServiceImpl.class, OrderPaymentService.class,
            com.project.w_3d_world.features.catalog.service.impl.CatalogServiceImpl.class,
            CatalogReadRepository.class})
    static class Config {}

    @MockitoBean
    StorefrontService storefrontService;

    @Autowired JdbcTemplate jdbc;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired OrderPaymentService payments;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired com.project.w_3d_world.features.catalog.service.CatalogService catalog;
    @Autowired AuditLogRepository audit;
    int buyer;
    int address;
    int variant;

    @BeforeEach
    void fixtures() {
        String key = UUID.randomUUID().toString();
        buyer = user("buyer-" + key);
        int seller = user("seller-" + key);
        jdbc.update("UPDATE users SET email_verified=true,phone_verified=true WHERE user_id=?", seller);
        jdbc.update("INSERT INTO user_roles(user_id,role_id,approval_status) SELECT ?,role_id,'ACTIVE' FROM roles WHERE role_code='SELLER'", seller);
        address = jdbc.queryForObject("""
                INSERT INTO user_addresses(user_id, recipient_name, address_line, city)
                VALUES (?, 'Buyer', 'Original address', 'City') RETURNING address_id
                """, Integer.class, buyer);
        int shop = jdbc.queryForObject("""
                INSERT INTO storefronts(owner_user_id,storefront_name,status,seller_verification_status,expires_at)
                VALUES (?, 'Shop','ACTIVE','VERIFIED',now()+interval '1 month') RETURNING storefront_id
                """, Integer.class, seller);
        int product = jdbc.queryForObject("""
                INSERT INTO products(storefront_id,title,status)
                VALUES (?, 'Product', 'PUBLISHED') RETURNING product_id
                """, Integer.class, shop);
        variant = jdbc.queryForObject("""
                INSERT INTO product_variants(product_id,sku,variant_attributes,unit_price,stock_quantity)
                VALUES (?, ?, '{"color":"white"}',10000,5) RETURNING variant_id
                """, Integer.class, product, key);
    }

    @Test
    void checkoutReplaysAndPersistsIndependentJsonSnapshot() {
        var item = carts.add(buyer, new ItemRequest(variant, 2)).items().getFirst();
        var request = new CheckoutRequest(List.of(item.itemId()), null, address, UUID.randomUUID().toString());
        var first = orders.checkout(buyer, request).getFirst();
        var second = orders.checkout(buyer, request).getFirst();
        assertEquals(first.id(), second.id());
        assertEquals(2, reserved());
        assertEquals("Original address", jdbc.queryForObject(
                "SELECT shipping_address_snapshot->>'addressLine' FROM physical_orders WHERE physical_order_id=?",
                String.class, first.id()));
        jdbc.update("UPDATE user_addresses SET address_line='New address' WHERE address_id=?", address);
        assertEquals("Original address", jdbc.queryForObject(
                "SELECT shipping_address_snapshot->>'addressLine' FROM physical_orders WHERE physical_order_id=?",
                String.class, first.id()));
    }

    @Test
    void priceMustBeAcknowledgedBeforeCheckout() {
        var item = carts.add(buyer, new ItemRequest(variant, 1)).items().getFirst();
        jdbc.update("UPDATE product_variants SET unit_price=12000 WHERE variant_id=?", variant);
        entityManager.flush();
        entityManager.clear();
        var refreshed = carts.get(buyer).items().getFirst();
        assertTrue(refreshed.priceChanged());
        assertEquals(0, refreshed.previousUnitPrice().compareTo(new BigDecimal("10000")));
        assertThrows(ApiException.class, () -> orders.checkout(buyer,
                new CheckoutRequest(List.of(item.itemId()), null, address, UUID.randomUUID().toString())));
    }

    @Test
    void expiryReleaseIsRepeatSafe() {
        var item = carts.add(buyer, new ItemRequest(variant, 1)).items().getFirst();
        var order = orders.checkout(buyer,
                new CheckoutRequest(List.of(item.itemId()), null, address, UUID.randomUUID().toString())).getFirst();
        // Keep the database expiry > created_at constraint satisfied.
        jdbc.update("""
                UPDATE physical_orders SET created_at=now()-interval '1 hour',
                reservation_expires_at=now()-interval '1 minute' WHERE physical_order_id=?
                """, order.id());
        // Clear cached entity state after changing timestamps directly.
        entityManager.clear();
        orders.releaseExpiredReservations();
        orders.releaseExpiredReservations();
        entityManager.flush();
        assertEquals(0, reserved());
    }

    @Autowired EntityManager entityManager;

    @Test
    void verifiedPaymentUpdatesOrderAndProcessingConsumesReservation() {
        var item = carts.add(buyer, new ItemRequest(variant, 1)).items().getFirst();
        var order = orders.checkout(buyer,
                new CheckoutRequest(List.of(item.itemId()), null, address, UUID.randomUUID().toString())).getFirst();
        var payment = new PaymentTransaction("PHYSICAL_ORDER", UUID.randomUUID().toString(),
                buyer, "FULL", order.totalAmount());
        payment.setSource("PHYSICAL_ORDER", order.id());
        transactions.saveAndFlush(payment);
        payment.markSuccess();
        payments.verified(payment);
        payments.verified(payment);
        int seller = jdbc.queryForObject("SELECT owner_user_id FROM storefronts WHERE storefront_id=?",
                Integer.class, order.storefrontId());
        orders.updateStatus(seller, order.id(), "PROCESSING");
        orders.updateStatus(seller, order.id(), "PROCESSING");
        entityManager.flush();
        assertEquals(0, reserved());
        assertEquals(4, jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE variant_id=?",
                Integer.class, variant));
    }

    @Test
    void catalogFiltersStorefrontBeforePagingAndAuditStoresValidJson() {
        assertTrue(catalog.search(null, null, 0, 20).totalElements() >= 1);
        jdbc.update("UPDATE storefronts SET expires_at=now()-interval '1 day'");
        entityManager.clear();
        assertEquals(0, catalog.search(null, null, 0, 20).totalElements());
        var entry = new AuditLog(
                buyer, "PROFILE_UPDATED", "USER", buyer, "Test", "verification_status=PENDING");
        audit.saveAndFlush(entry);
        assertEquals("verification_status=PENDING", jdbc.queryForObject(
                "SELECT metadata->>'detail' FROM audit_logs WHERE audit_log_id=?",
                String.class, entry.getId()));
    }

    private int user(String name) {
        return jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES (?, 'mock', ?) RETURNING user_id",
                Integer.class, name + "@example.test", name);
    }

    private int reserved() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT reserved_quantity FROM product_variants WHERE variant_id=?",
                Integer.class, variant);
    }
}
