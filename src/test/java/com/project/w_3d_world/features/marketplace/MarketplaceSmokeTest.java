package com.project.w_3d_world.features.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.project.w_3d_world.W3dWorldApplication;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.order.service.OrderPaymentService;
import com.project.w_3d_world.features.payment.entity.PaymentTransaction;
import com.project.w_3d_world.features.payment.repository.PaymentTransactionRepository;
import com.project.w_3d_world.features.payment.service.PaymentService;
import com.project.w_3d_world.features.storefront.service.StorefrontService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.*;

/** Explicit opt-in: runs only on the named disposable local database, never on Supabase. */
@EnabledIfSystemProperty(named = "marketplace.pg.integration", matches = "true")
@SpringBootTest(classes = W3dWorldApplication.class, properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/marketplacecheck",
        "spring.datasource.username=canhan7325", "spring.datasource.password=",
        "app.auth.email-worker-enabled=false", "spring.sql.init.mode=never", "app.ghtk.enabled=false"
})
class MarketplaceSmokeTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired StorefrontService stores;
    @Autowired OrderPaymentService orderPayments;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean EmailService email;
    @MockitoBean CloudinaryStorage cloudinary;
    @MockitoBean PaymentService paymentProvider;
    MockMvc mvc;
    int admin, seller, buyer, other, shop, product, variant;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE TABLE users,roles,packaging_profiles CASCADE");
        for (String role : List.of("CUSTOMER", "SELLER", "ADMIN", "PRINT_STAFF", "DESIGNER")) {
            jdbc.update("INSERT INTO roles(role_code,role_name) VALUES (?,?)", role, role);
        }
        admin = user("admin", "ADMIN");
        seller = user("seller", "SELLER");
        buyer = user("buyer", "CUSTOMER");
        other = user("other", "CUSTOMER");
        shop = jdbc.queryForObject("""
                INSERT INTO storefronts(owner_user_id,storefront_name,status,seller_verification_status,expires_at)
                VALUES (?,'Smoke shop','ACTIVE','VERIFIED',now()+interval '1 month') RETURNING storefront_id
                """, Integer.class, seller);
        product = jdbc.queryForObject("""
                INSERT INTO products(storefront_id,title,description,category,status,badge_type)
                VALUES (?,'Printed model','A physical model','Models','PUBLISHED','FEATURED') RETURNING product_id
                """, Integer.class, shop);
        variant = jdbc.queryForObject("""
                INSERT INTO product_variants(product_id,sku,unit_price,stock_quantity)
                VALUES (?, 'SMOKE-PRIMARY',10000,1) RETURNING variant_id
                """, Integer.class, product);
        jdbc.update("INSERT INTO product_media(product_id,media_type,media_url) VALUES (?,'IMAGE','https://example.com/model.png')", product);
    }

    @Test
    void catalogSecuritySwaggerAndValidation() throws Exception {
        JsonNode page = send(get("/api/catalog").param("sort", "price_asc").param("minPrice", "5000"), 0, 200);
        assertEquals(product, page.at("/data/items/0/id").asInt());
        assertEquals(10000, page.at("/data/items/0/price").asInt());
        assertTrue(page.at("/data/items/0/available").asBoolean());
        send(get("/api/catalog").param("minPrice", "20000").param("maxPrice", "1000"), 0, 400);
        send(get("/api/storefronts/" + shop), 0, 200);
        send(get("/api/storefronts/" + shop + "/products"), 0, 200);
        send(get("/api/storefronts/me"), 0, 401);
        send(get("/api/seller/products"), buyer, 403);
        send(get("/api/catalog/" + product + "/reviews"), 0, 200);
        JsonNode docs = send(get("/v3/api-docs"), 0, 200);
        assertTrue(docs.at("/paths").has("/api/seller/products/{id}/submit"));
        assertTrue(docs.at("/paths").has("/api/orders/{orderId}/confirm-received"));
        assertNull(jdbc.queryForObject("SELECT to_regclass('public.flyway_schema_history')::text", String.class));
    }

    @Test
    void sellerLifecycleAndStaleModeration() throws Exception {
        String body = """
                {"title":"New print","category":"Models","variants":[
                  {"sku":"NEW-PRINT","price":15000,"stock":2}]}
                """;
        int id = send(json(post("/api/seller/products"), body), seller, 200).at("/data/id").asInt();
        send(get("/api/seller/products/" + id), buyer, 403);
        send(get("/api/catalog/" + id), 0, 404);
        when(cloudinary.uploadProduct(eq(id), any(), eq("image/png")))
                .thenReturn(new CloudinaryStorage.UploadedImage("https://res.cloudinary.com/demo/new.png", "Products/new"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        send(multipart("/api/seller/products/" + id + "/media")
                .file(new MockMultipartFile("file", "model.png", "image/png", bytes.toByteArray())), seller, 200);
        String revision = send(post("/api/seller/products/" + id + "/submit"), seller, 200)
                .at("/data/revision").asText();
        send(json(post("/api/admin/products/" + id + "/approve"),
                "{\"revision\":\"wrong\",\"reason\":\"Checked\"}"), admin, 409);
        send(json(post("/api/admin/products/" + id + "/approve"),
                "{\"revision\":\"" + revision + "\",\"reason\":\"Checked\"}"), admin, 200);
        send(get("/api/catalog/" + id), 0, 200);
        send(patch("/api/seller/products/" + id + "/unpublish"), seller, 200);
        send(get("/api/catalog/" + id), 0, 404);
        int report = send(json(post("/api/reports"), "{\"productId\":" + product + ",\"reason\":\"Violation\"}"), buyer, 200)
                .at("/data/id").asInt();
        send(json(patch("/api/admin/reports/" + report),
                "{\"status\":\"RESOLVED\",\"reason\":\"Evidence checked\",\"hideProduct\":true}"), admin, 200);
        send(get("/api/catalog/" + product), 0, 404);
    }

    @Test
    void concurrentCheckoutCancelAndOrderOwnership() throws Exception {
        int item1 = cartItem(buyer);
        int item2 = cartItem(other);
        int address1 = address(buyer), address2 = address(other);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Future<MvcResult> first = pool.submit(() -> { latch.await(); return checkout(buyer, item1, address1, "checkout-a"); });
            Future<MvcResult> second = pool.submit(() -> { latch.await(); return checkout(other, item2, address2, "checkout-b"); });
            latch.countDown();
            MvcResult a = first.get(20, TimeUnit.SECONDS), b = second.get(20, TimeUnit.SECONDS);
            assertEquals(Set.of(200, 400), Set.of(a.getResponse().getStatus(), b.getResponse().getStatus()));
            boolean wonFirst = a.getResponse().getStatus() == 200;
            MvcResult win = wonFirst ? a : b;
            int owner = wonFirst ? buyer : other, item = wonFirst ? item1 : item2, addr = wonFirst ? address1 : address2;
            int order = mapper.readTree(win.getResponse().getContentAsString()).at("/data/0/id").asInt();
            assertEquals(1, jdbc.queryForObject("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", Integer.class, variant));
            assertEquals(200, checkout(owner, item, addr, wonFirst ? "checkout-a" : "checkout-b").getResponse().getStatus());
            send(get("/api/orders/" + order), owner == buyer ? other : buyer, 404);
            send(get("/api/orders/" + order + "/history"), owner, 200);
            send(json(post("/api/orders/" + order + "/cancel"), "{\"reason\":\"Changed my mind\"}"), owner, 200);
            send(json(post("/api/orders/" + order + "/cancel"), "{\"reason\":\"Retry\"}"), owner, 200);
            assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", Integer.class, variant));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void fulfillmentPackagesDeliveryAndReceipt() throws Exception {
        int item = cartItem(buyer);
        var checkout = checkout(buyer, item, address(buyer), "fulfillment");
        assertEquals(200, checkout.getResponse().getStatus());
        int order = mapper.readTree(checkout.getResponse().getContentAsString()).at("/data/0/id").asInt();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PaymentTransaction payment = new PaymentTransaction("PHYSICAL_ORDER", UUID.randomUUID().toString(),
                    buyer, "FULL", new BigDecimal("10000"));
            payment.setSource("PHYSICAL_ORDER", order);
            payment.markSuccess();
            transactions.saveAndFlush(payment);
            orderPayments.verified(payment);
        });
        send(json(patch("/api/orders/" + order + "/status"), "{\"status\":\"PROCESSING\"}"), seller, 200);
        jdbc.update("UPDATE product_variants SET product_weight_grams=100,product_length_cm=5,product_width_cm=5,product_height_cm=5 WHERE variant_id=?", variant);
        int profile = jdbc.queryForObject("INSERT INTO packaging_profiles(code,name,inner_length_cm,inner_width_cm,inner_height_cm,"
                + "max_product_weight_grams,package_weight_grams) VALUES ('SMOKE-BOX','Box',20,20,20,1000,50) RETURNING packaging_profile_id", Integer.class);
        int orderedItem = jdbc.queryForObject("SELECT physical_order_item_id FROM physical_order_items WHERE physical_order_id=?", Integer.class, order);
        String packages = "{\"packages\":[{\"packagingProfileId\":" + profile + ",\"items\":[{\"orderItemId\":" + orderedItem + ",\"quantity\":1}]}]}";
        send(json(post("/api/orders/" + order + "/packages"), packages), buyer, 403);
        JsonNode prepared = send(json(post("/api/orders/" + order + "/packages"), packages), seller, 200);
        assertEquals(150, prepared.at("/data/0/packageWeightGrams").asInt());
        send(get("/api/orders/" + order + "/packages"), buyer, 200);
        int shipment = send(json(post("/api/orders/" + order + "/shipment"), "{\"carrierName\":\"DEMO\"}"), seller, 200)
                .at("/data/id").asInt();
        send(json(patch("/api/shipments/" + shipment + "/event"), "{\"event\":\"DELIVERED\"}"), seller, 200);
        send(get("/api/orders/" + order + "/shipments"), seller, 200);
        send(post("/api/orders/" + order + "/confirm-received"), buyer, 200);
        send(post("/api/orders/" + order + "/confirm-received"), buyer, 200);
        assertNotNull(jdbc.queryForObject("SELECT refund_deadline_at FROM physical_orders WHERE physical_order_id=?", java.sql.Timestamp.class, order));
        assertEquals(0, jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE variant_id=?", Integer.class, variant));
    }

    @Test
    void subscriptionPaymentActivationAndRenewalAreIdempotent() throws Exception {
        int bank = jdbc.queryForObject("""
                INSERT INTO user_bank_accounts(user_id,bank_code,bank_name,account_holder,account_number_token,
                account_number_masked,verification_status,is_default) VALUES (?,'DEMO','Demo','Seller',
                'sandbox','****1234','VERIFIED',true) RETURNING bank_account_id
                """, Integer.class, seller);
        jdbc.update("UPDATE users SET seller_verification_state=jsonb_build_object('status','VERIFIED',"
                + "'phoneSnapshot',phone,'bankAccountId',?,'submittedAt',1) WHERE user_id=?", bank, seller);
        var pending = stores.subscribe(seller);
        assertEquals(pending.id(), stores.subscribe(seller).id());
        Instant originalExpiry = jdbc.queryForObject("SELECT expires_at FROM storefronts WHERE storefront_id=?",
                java.sql.Timestamp.class, shop).toInstant();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            PaymentTransaction payment = new PaymentTransaction("SUBSCRIPTION", UUID.randomUUID().toString(), seller,
                    "SUBSCRIPTION", pending.fee());
            payment.setSource("SUBSCRIPTION", pending.id());
            payment.markSuccess();
            transactions.saveAndFlush(payment);
            orderPayments.verified(payment);
            orderPayments.verified(payment);
        });
        Instant expiry = jdbc.queryForObject("SELECT expires_at FROM storefronts WHERE storefront_id=?",
                java.sql.Timestamp.class, shop).toInstant();
        assertEquals(originalExpiry.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant(), expiry);
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM storefront_subscriptions WHERE subscription_id=?",
                String.class, pending.id()));
        send(get("/api/storefronts/me/subscription"), seller, 200);
    }

    private int user(String name, String role) {
        int id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,phone,email_verified,phone_verified) "
                + "VALUES (?,'unused',?,'0901234567',true,true) RETURNING user_id", Integer.class, name + "@example.com", name);
        jdbc.update("INSERT INTO user_roles(user_id,role_id,approval_status) SELECT ?,role_id,'ACTIVE' FROM roles WHERE role_code=?", id, role);
        return id;
    }

    private int address(int user) {
        return jdbc.queryForObject("INSERT INTO user_addresses(user_id,recipient_name,address_line,city) "
                + "VALUES (?,'Buyer','Address','City') RETURNING address_id", Integer.class, user);
    }

    private int cartItem(int user) throws Exception {
        return send(json(post("/api/cart/items"), "{\"variantId\":" + variant + ",\"quantity\":1}"), user, 200)
                .at("/data/items/0/itemId").asInt();
    }

    private MvcResult checkout(int user, int item, int address, String key) throws Exception {
        return mvc.perform(auth(json(post("/api/orders/checkout"), "{\"cartItemIds\":[" + item
                + "],\"shippingAddressId\":" + address + ",\"idempotencyKey\":\"" + key + "\"}"), user)).andReturn();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private AbstractMockHttpServletRequestBuilder<?> auth(AbstractMockHttpServletRequestBuilder<?> request, int user) {
        if (user == 0) {
            return request;
        }
        long version = jdbc.queryForObject("SELECT auth_version FROM users WHERE user_id=?", Long.class, user);
        return request.header("Authorization", "Bearer " + jwt.generateToken(user, Set.of("CUSTOMER"), "0901234567", "test@example.com", version).getTokenValue());
    }

    private JsonNode send(AbstractMockHttpServletRequestBuilder<?> request, int user, int expected) throws Exception {
        MvcResult result = mvc.perform(auth(request, user)).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
