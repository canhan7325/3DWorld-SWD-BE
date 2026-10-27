package com.project.w_3d_world.features.shipping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.project.w_3d_world.W3dWorldApplication;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.payment.service.PaymentService;
import com.project.w_3d_world.features.shipping.ghtk.GhtkClient;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.*;

@EnabledIfSystemProperty(named = "ghtk.pg.integration", matches = "true")
@SpringBootTest(classes = W3dWorldApplication.class, properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55441/ghtkcheck",
        "spring.datasource.username=canhan7325", "spring.datasource.password=",
        "app.ghtk.enabled=true", "app.ghtk.token=local-test-only", "app.ghtk.padding-cm=2",
        "app.auth.email-worker-enabled=false", "spring.sql.init.mode=never"
})
class GhtkQuotePostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @MockitoBean GhtkClient carrier;
    @MockitoBean EmailService email;
    @MockitoBean CloudinaryStorage images;
    @MockitoBean PaymentService payments;
    MockMvc mvc;
    int buyer, seller, shop, item, address, pickup, variant;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE users,roles,packaging_profiles CASCADE");
        jdbc.update("INSERT INTO roles(role_code,role_name) VALUES ('CUSTOMER','Customer'),('SELLER','Seller')");
        buyer = user("buyer");
        seller = user("seller");
        jdbc.update("INSERT INTO user_roles(user_id,role_id,approval_status) SELECT ?,role_id,'ACTIVE' FROM roles WHERE role_code='SELLER'", seller);
        shop = jdbc.queryForObject("INSERT INTO storefronts(owner_user_id,storefront_name,status,seller_verification_status,expires_at) "
                + "VALUES (?,'Test','ACTIVE','VERIFIED',now()+interval '1 month') RETURNING storefront_id", Integer.class, seller);
        int product = jdbc.queryForObject("INSERT INTO products(storefront_id,title,status) VALUES (?,'Print','PUBLISHED') RETURNING product_id", Integer.class, shop);
        variant = jdbc.queryForObject("""
                INSERT INTO product_variants(product_id,sku,unit_price,stock_quantity,product_weight_grams,
                product_length_cm,product_width_cm,product_height_cm,fragile)
                VALUES (?,'PRINT',10000,5,100,5,4,3,true) RETURNING variant_id
                """, Integer.class, product);
        int cart = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING cart_id", Integer.class, buyer);
        item = jdbc.queryForObject("INSERT INTO cart_items(cart_id,variant_id,quantity,price_at_added) VALUES (?,?,2,10000) RETURNING cart_item_id",
                Integer.class, cart, variant);
        address = address(buyer);
        pickup = address(seller);
        jdbc.update("""
                INSERT INTO packaging_profiles(code,name,inner_length_cm,inner_width_cm,inner_height_cm,
                max_product_weight_grams,package_weight_grams,fragile_padding_weight_grams,supports_fragile)
                VALUES ('BOX','Box',30,20,10,1000,50,20,true)
                """);
        when(carrier.quote(any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new GhtkClient.Fee(new BigDecimal("30000"), new BigDecimal("1000"), "area1", "[]", false));
    }

    @Test
    void quoteAndCheckoutPersistFeePackagesAndReplayWithoutCallingProviderAgain() throws Exception {
        JsonNode quote = send(post("/api/shipping/quote"), Map.of("cartItemIds", List.of(item), "shippingAddressId", address), 200);
        assertEquals(31000, quote.at("/data/totalShippingFee").asInt());
        assertEquals(270, quote.at("/data/shops/0/selected/packing/parcels/0/totalWeight").asInt());
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", Integer.class, variant));
        Map<String, Object> request = Map.of("cartItemIds", List.of(item), "shippingAddressId", address,
                "idempotencyKey", "shipping-test", "shippingQuoteToken", quote.at("/data/quoteToken").asText());
        JsonNode first = send(post("/api/orders/checkout"), request, 200);
        assertEquals(51000, first.at("/data/0/totalAmount").asInt());
        int order = first.at("/data/0/id").asInt();
        assertEquals(order, send(post("/api/orders/checkout"), request, 200).at("/data/0/id").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM order_shipping_packages WHERE physical_order_id=?", Integer.class, order));
        assertEquals(2, jdbc.queryForObject("SELECT sum(quantity) FROM order_shipping_package_items WHERE physical_order_id=?", Integer.class, order));
        assertEquals("GHTK_FLOOR_PACK_V1", jdbc.queryForObject("SELECT shipping_calculation_version FROM physical_orders WHERE physical_order_id=?", String.class, order));
        verify(carrier, times(1)).quote(any(), any(), eq(270), eq(new BigDecimal("20000.00")), eq(true));
    }

    @Test
    void missingQuoteTamperingAndAddressChangesCannotCheckout() throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("cartItemIds", List.of(item), "shippingAddressId", address, "idempotencyKey", "changed"));
        send(post("/api/orders/checkout"), request, 400);
        JsonNode quote = send(post("/api/shipping/quote"), Map.of("cartItemIds", List.of(item), "shippingAddressId", address), 200);
        String token = quote.at("/data/quoteToken").asText();
        request.put("shippingQuoteToken", "x" + token);
        send(post("/api/orders/checkout"), request, 400);
        request.put("shippingQuoteToken", token);
        jdbc.update("UPDATE user_addresses SET ward='Changed ward' WHERE address_id=?", address);
        send(post("/api/orders/checkout"), request, 409);
        assertEquals(0, jdbc.queryForObject("SELECT reserved_quantity FROM product_variants WHERE variant_id=?", Integer.class, variant));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM physical_orders", Integer.class));
    }

    @Test
    void missingPickupAddressFailsBeforeAnyCarrierCall() throws Exception {
        jdbc.update("UPDATE user_addresses SET is_default=false WHERE address_id=?", pickup);
        send(post("/api/shipping/quote"), Map.of("cartItemIds", List.of(item), "shippingAddressId", address), 400);
        verifyNoInteractions(carrier);
    }

    int user(String name) {
        return jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,email_verified,phone_verified) "
                + "VALUES (?,'unused',?,true,true) RETURNING user_id", Integer.class, name + "@test.invalid", name);
    }
    int address(int user) {
        return jdbc.queryForObject("INSERT INTO user_addresses(user_id,recipient_name,address_line,province,ward,is_default) "
                + "VALUES (?,'Test','1 Main Street','Hà Nội','Test ward',true) RETURNING address_id", Integer.class, user);
    }
    JsonNode send(MockHttpServletRequestBuilder request, Map<String, Object> body, int expected) throws Exception {
        var result = mvc.perform(request.header("Authorization", "Bearer " + jwt.generateToken(buyer, Set.of("CUSTOMER"), "", "buyer@test.invalid").getTokenValue())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
