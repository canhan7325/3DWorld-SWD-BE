package com.project.w_3d_world.features.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.project.w_3d_world.W3dWorldApplication;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.order.service.OrderService;
import com.project.w_3d_world.features.payment.service.ZaloPayClient;
import com.project.w_3d_world.features.payment.service.PaymentSourceResolver;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.*;

@EnabledIfSystemProperty(named = "cod.pg.integration", matches = "true")
@SpringBootTest(classes = W3dWorldApplication.class, properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55442/codcheck",
        "spring.datasource.username=canhan7325", "spring.datasource.password=",
        "app.ghtk.enabled=false", "app.auth.email-worker-enabled=false", "spring.sql.init.mode=never",
        "app.order.shipping-fee=30000", "app.order.cod-acceptance-ttl-minutes=1440"
})
class CodCheckoutPostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired OrderService orders;
    @Autowired PaymentSourceResolver sources;
    @MockitoBean ZaloPayClient zaloPay;
    @MockitoBean EmailService email;
    @MockitoBean CloudinaryStorage images;
    MockMvc mvc;
    int buyer, seller, admin, staff, other, variant, cartItem, address;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE users,roles CASCADE");
        for (String role : List.of("CUSTOMER", "SELLER", "ADMIN", "PRINT_STAFF")) {
            jdbc.update("INSERT INTO roles(role_code,role_name) VALUES (?,?)", role, role);
        }
        buyer = user("buyer", "CUSTOMER");
        seller = user("seller", "SELLER");
        admin = user("admin", "ADMIN");
        staff = user("staff", "PRINT_STAFF");
        other = user("other", "CUSTOMER");
        int shop = jdbc.queryForObject("INSERT INTO storefronts(owner_user_id,storefront_name,status,seller_verification_status,expires_at) "
                + "VALUES (?,'Shop','ACTIVE','VERIFIED',now()+interval '1 month') RETURNING storefront_id", Integer.class, seller);
        int product = jdbc.queryForObject("INSERT INTO products(storefront_id,title,status) VALUES (?,'Print','PUBLISHED') RETURNING product_id", Integer.class, shop);
        variant = jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,unit_price,stock_quantity) VALUES (?,'COD-SKU',10000,1) RETURNING variant_id", Integer.class, product);
        int cart = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING cart_id", Integer.class, buyer);
        cartItem = jdbc.queryForObject("INSERT INTO cart_items(cart_id,variant_id,quantity,price_at_added) VALUES (?,?,1,10000) RETURNING cart_item_id", Integer.class, cart, variant);
        address = jdbc.queryForObject("INSERT INTO user_addresses(user_id,recipient_name,address_line,province,ward) VALUES (?,'Buyer','1 Main Street','Province','Ward') RETURNING address_id", Integer.class, buyer);
    }

    @Test
    void codCheckoutIncludesShippingReplaysAndNeverRequiresZaloPay() throws Exception {
        JsonNode first = send(post("/api/orders/checkout"), buyer, checkout("COD"), 200);
        int id = first.at("/data/0/id").asInt();
        assertEquals("CREATED", first.at("/data/0/status").asText());
        assertEquals("COD", first.at("/data/0/paymentMethod").asText());
        assertFalse(first.at("/data/0/onlinePaymentRequired").asBoolean());
        assertEquals(40000, first.at("/data/0/totalAmount").asInt());
        assertEquals(id, send(post("/api/orders/checkout"), buyer, checkout("COD"), 200).at("/data/0/id").asInt());
        send(post("/api/orders/checkout"), buyer, checkout("GATEWAY"), 400);
        var payment = send(get("/api/orders/" + id + "/cod-payment"), buyer, null, 200);
        assertEquals("PENDING_COLLECTION", payment.at("/data/collectionStatus").asText());
        assertEquals(40000, payment.at("/data/amountToCollect").asInt());
        assertThrows(ApiException.class, () -> sources.resolve(buyer, "PHYSICAL_ORDER", id, "FULL", null));
        send(post("/api/payments/zalopay"), buyer,
                Map.of("sourceType", "PHYSICAL_ORDER", "sourceId", id, "stage", "FULL"), 400);
        send(patch("/api/orders/" + id + "/status"), seller, Map.of("status", "PROCESSING"), 200);
        send(patch("/api/orders/" + id + "/status"), seller, Map.of("status", "PROCESSING"), 200);
        assertEquals(0, scalar("SELECT stock_quantity FROM product_variants WHERE variant_id=?", variant));
        assertEquals(0, scalar("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", variant));
        verifyNoInteractions(zaloPay);
    }

    @Test
    void gatewayStillRequiresVerifiedPaymentAndInvalidMethodsAreRejected() throws Exception {
        send(post("/api/orders/checkout"), buyer, checkout("INVALID"), 400);
        JsonNode result = send(post("/api/orders/checkout"), buyer, checkout("ZALOPAY"), 200);
        int id = result.at("/data/0/id").asInt();
        assertEquals("GATEWAY", result.at("/data/0/paymentMethod").asText());
        assertEquals("PENDING_PAYMENT", result.at("/data/0/status").asText());
        assertTrue(result.at("/data/0/onlinePaymentRequired").asBoolean());
        send(patch("/api/orders/" + id + "/status"), seller, Map.of("status", "PROCESSING"), 400);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE settlement_mode='COD'", Integer.class));
    }

    @Test
    void cancellationAndExpiryReleaseStockAndCancelCashObligationOnce() throws Exception {
        int id = codOrder();
        send(post("/api/orders/" + id + "/cancel"), buyer, Map.of("reason", "Changed mind"), 200);
        send(post("/api/orders/" + id + "/cancel"), buyer, Map.of("reason", "Retry"), 200);
        assertEquals(0, scalar("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", variant));
        assertEquals("CANCELLED", send(get("/api/orders/" + id + "/cod-payment"), buyer, null, 200).at("/data/status").asText());
        assertEquals(0, send(get("/api/orders/" + id + "/cod-payment"), buyer, null, 200).at("/data/amountToCollect").asInt());
        jdbc.update("UPDATE physical_orders SET created_at=now()-interval '2 days',reservation_expires_at=now()-interval '1 day' WHERE physical_order_id=?", id);
        orders.releaseExpiredReservations();
        assertEquals(1, scalar("SELECT stock_quantity FROM product_variants WHERE variant_id=?", variant));
    }

    @Test
    void expiredAcceptanceCancelsCodAndCannotBeProcessed() throws Exception {
        int id = codOrder();
        jdbc.update("UPDATE physical_orders SET created_at=now()-interval '2 days',reservation_expires_at=now()-interval '1 day' WHERE physical_order_id=?", id);
        send(patch("/api/orders/" + id + "/status"), seller, Map.of("status", "PROCESSING"), 409);
        orders.releaseExpiredReservations();
        orders.releaseExpiredReservations();
        assertEquals("CANCELLED", send(get("/api/orders/" + id + "/cod-payment"), buyer, null, 200).at("/data/status").asText());
        assertEquals(0, scalar("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", variant));
    }

    @Test
    void deliveryCollectionAndSettlementStaySeparateAndReplaySafely() throws Exception {
        int id = codOrder();
        Map<String, Object> premature = evidence("collect-1", 40000, Instant.now().minusSeconds(1));
        send(post("/api/admin/orders/" + id + "/cod/collection"), staff, premature, 409);
        send(get("/api/orders/" + id + "/cod-payment"), other, null, 403);
        send(patch("/api/orders/" + id + "/status"), seller, Map.of("status", "PROCESSING"), 200);
        int shipment = send(post("/api/orders/" + id + "/shipment"), seller, Map.of("carrierName", "DEMO"), 200).at("/data/id").asInt();
        send(patch("/api/shipments/" + shipment + "/event"), seller, Map.of("event", "DELIVERED"), 200);
        assertEquals("PENDING", send(get("/api/orders/" + id + "/cod-payment"), buyer, null, 200).at("/data/status").asText());
        Instant time = Instant.now();
        Map<String, Object> collected = evidence("collect-1", 40000, time);
        send(post("/api/admin/orders/" + id + "/cod/settlement"), admin, evidence("settle-1", 40000, time), 409);
        send(post("/api/admin/orders/" + id + "/cod/collection"), buyer, collected, 403);
        send(post("/api/admin/orders/" + id + "/cod/collection"), staff, evidence("collect-1", 10000, time), 409);
        JsonNode cash = send(post("/api/admin/orders/" + id + "/cod/collection"), staff, collected, 200);
        assertEquals("PROCESSING", cash.at("/data/status").asText());
        assertEquals("PENDING_SETTLEMENT", cash.at("/data/collectionStatus").asText());
        assertEquals(0, cash.at("/data/amountToCollect").asInt());
        send(post("/api/admin/orders/" + id + "/cod/collection"), staff, collected, 200);
        send(post("/api/admin/orders/" + id + "/cod/collection"), staff, evidence("another-ref", 40000, time), 409);
        Map<String, Object> settled = evidence("settle-1", 40000, Instant.now());
        send(post("/api/admin/orders/" + id + "/cod/settlement"), staff, settled, 403);
        send(post("/api/admin/orders/" + id + "/cod/settlement"), admin, settled, 200);
        send(post("/api/admin/orders/" + id + "/cod/settlement"), admin, settled, 200);
        assertEquals("SUCCESS", send(get("/api/orders/" + id + "/cod-payment"), buyer, null, 200).at("/data/status").asText());
        assertEquals("DELIVERED", send(get("/api/orders/" + id), buyer, null, 200).at("/data/status").asText());
        assertEquals(1, scalar("SELECT count(*) FROM audit_logs WHERE action_type='COD_SETTLED' AND entity_id=?", id));
        assertEquals(0, scalar("SELECT count(*) FROM wallet_ledgers l JOIN wallets w ON w.wallet_id=l.wallet_id WHERE w.user_id=?", seller));
        verifyNoInteractions(zaloPay);
    }

    @Test
    void concurrentCodBuyersCannotReserveTheLastUnitTwice() throws Exception {
        int otherAddress = jdbc.queryForObject("INSERT INTO user_addresses(user_id,recipient_name,address_line) VALUES (?,'Other','Street') RETURNING address_id", Integer.class, other);
        int cart = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING cart_id", Integer.class, other);
        int otherItem = jdbc.queryForObject("INSERT INTO cart_items(cart_id,variant_id,quantity,price_at_added) VALUES (?,?,1,10000) RETURNING cart_item_id", Integer.class, cart, variant);
        Map<String, Object> second = Map.of("cartItemIds", List.of(otherItem), "shippingAddressId", otherAddress, "idempotencyKey", "other-cod", "paymentMethod", "COD");
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return request(post("/api/orders/checkout"), buyer, checkout("COD")).getResponse().getStatus(); });
            var b = pool.submit(() -> { start.await(); return request(post("/api/orders/checkout"), other, second).getResponse().getStatus(); });
            start.countDown();
            assertEquals(Set.of(200, 400), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
        }
        assertEquals(1, scalar("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", variant));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE settlement_mode='COD'", Integer.class));
    }

    int codOrder() throws Exception { return send(post("/api/orders/checkout"), buyer, checkout("COD"), 200).at("/data/0/id").asInt(); }
    int scalar(String sql, int id) { return jdbc.queryForObject(sql, Integer.class, id); }
    Map<String, Object> checkout(String method) { return Map.of("cartItemIds", List.of(cartItem), "shippingAddressId", address, "idempotencyKey", "cod-test", "paymentMethod", method); }
    Map<String, Object> evidence(String reference, int amount, Instant time) { return Map.of("amount", amount, "reference", reference, "evidenceKey", "manual-test-evidence", "reason", "Sandbox evidence checked", "occurredAt", time.toString()); }
    int user(String name, String role) {
        int id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,email_verified,phone_verified) VALUES (?,'unused',?,true,true) RETURNING user_id", Integer.class, name + "@test.invalid", name);
        jdbc.update("INSERT INTO user_roles(user_id,role_id,approval_status) SELECT ?,role_id,'ACTIVE' FROM roles WHERE role_code=?", id, role);
        return id;
    }
    MvcResult request(MockHttpServletRequestBuilder request, int actor, Map<String, Object> body) throws Exception {
        request.header("Authorization", "Bearer " + jwt.generateToken(actor, Set.of("CUSTOMER"), "", "test@test.invalid").getTokenValue());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request).andReturn();
    }
    JsonNode send(MockHttpServletRequestBuilder request, int actor, Map<String, Object> body, int expected) throws Exception {
        var result = request(request, actor, body);
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
