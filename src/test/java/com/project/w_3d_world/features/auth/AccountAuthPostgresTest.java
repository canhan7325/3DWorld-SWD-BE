package com.project.w_3d_world.features.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.auth.controller.*;
import com.project.w_3d_world.features.auth.delivery.*;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.service.*;
import com.project.w_3d_world.features.auth.service.impl.*;
import com.project.w_3d_world.features.order.controller.OrderController;
import com.project.w_3d_world.features.order.service.impl.OrderServiceImpl;
import com.project.w_3d_world.features.user.controller.*;
import com.project.w_3d_world.features.user.service.*;
import com.project.w_3d_world.features.user.service.impl.UserProfileServiceImpl;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named="auth.pg.integration", matches="true")
@SpringBootTest(classes=AccountAuthPostgresTest.Config.class, properties={
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/authcheck",
        "spring.datasource.username=canhan7325", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
        "spring.sql.init.mode=never", "spring.jpa.open-in-view=false",
        "app.auth.email-worker-enabled=false", "app.auth.email-resend-cooldown-seconds=0",
        "app.auth.token-expiration=60000", "app.auth.base-url=http://localhost:8080",
        "app.jwt.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "app.jwt.expiration=60000"
})
class AccountAuthPostgresTest {
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @EntityScan("com.project.w_3d_world.features")
    @EnableJpaRepositories("com.project.w_3d_world.features")
    @Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtService.class,
            AuthServiceImpl.class, AuthSessionServiceImpl.class, EmailTokenServiceImpl.class,
            CapabilityServiceImpl.class, AccountAuthorization.class, AdminAccountService.class,
            SellerVerificationService.class, EmailStateCodec.class, EmailTokenEncryption.class,
            EmailDeliveryService.class, UserProfileServiceImpl.class, BankVerificationService.class,
            AvatarImageValidator.class, AvatarService.class, AuthController.class, EmailAuthController.class,
            AdminAccountController.class, SellerVerificationController.class, BankReviewController.class,
            UserProfileController.class, OrderController.class, OrderServiceImpl.class})
    static class Config {
        @Bean EmailService email() { return mock(EmailService.class); }
        @Bean CloudinaryStorage cloudinary() { return mock(CloudinaryStorage.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @Autowired EmailService email;
    @Autowired EmailDeliveryService deliveries;
    @Autowired JwtService jwt;
    @Autowired CloudinaryStorage storage;
    MockMvc mvc;
    int admin;
    String adminToken;
    final String password="Admin@123";

    @BeforeEach
    void setup() throws Exception {
        // This test is opt-in and uses only the explicit authcheck LOCAL database above.
        var tables=jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'",String.class);
        jdbc.execute("TRUNCATE "+String.join(",",tables)+" RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO roles(role_code,role_name,is_internal) VALUES "
                +"('CUSTOMER','Customer',false),('SELLER','Seller',false),('DESIGNER','Designer',false),"
                +"('PRINT_STAFF','Print Staff',true),('ADMIN','Admin',true)");
        reset(email, storage);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin=account("admin@example.test",true,"ADMIN");
        adminToken=login("admin@example.test",password);
    }

    @Test
    void registerVerifyImmediateAndPreventRoleInjectionAndReplay() throws Exception {
        assertEquals(400,call(post("/api/auth/register"),null,Map.of("email","evil@example.test",
                "password",password,"fullName","Test","role","ADMIN")).status);
        var result=call(post("/api/auth/register"),null,Map.of("email","user@example.test",
                "password",password,"fullName","Test User"));
        assertEquals(201,result.status);
        int id=result.json.path("data").path("userId").asInt();
        assertTrue(passwords.matches(password,jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,id)));
        assertEquals(List.of("CUSTOMER"),jdbc.queryForList("SELECT r.role_code FROM roles r JOIN user_roles ur USING(role_id) WHERE user_id=?",String.class,id));
        String token=deliver(id);
        assertEquals(400,call(post("/api/auth/verify-email"),null,Map.of("email","other@example.test","token",token)).status);
        assertEquals(200,call(post("/api/auth/verify-email"),null,Map.of("email","user@example.test","token",token)).status);
        assertEquals(400,call(post("/api/auth/verify-email"),null,Map.of("email","user@example.test","token",token)).status);
        assertEquals(200,call(get("/api/auth/me"),login("user@example.test",password),null).status);
    }

    @Test
    void separatesTokenPurposesAndRejectsTampering() throws Exception {
        int id=register("verify@example.test");
        String verification=deliver(id);
        assertEquals(401,call(get("/api/auth/me"),verification,null).status);
        String access=login("verify@example.test",password);
        assertEquals(400,call(post("/api/auth/verify-email"),null,Map.of("email","verify@example.test","token",access)).status);
        String tampered=verification.substring(0,verification.length()-8)+"AAAAAAAA";
        assertEquals(400,call(post("/api/auth/verify-email"),null,Map.of("email","verify@example.test","token",tampered)).status);
    }

    @Test
    void emailRetryIsDurableAndResendInvalidatesOldLink() throws Exception {
        int id=register("retry@example.test");
        doThrow(new MailSendException("provider unavailable")).doNothing().when(email).sendLink(anyString(),anyString(),anyString(),anyString());
        deliveries.dispatch(id);
        assertEquals("FAILED",deliveryStatus(id,"VERIFY_EMAIL"));
        assertFalse(jdbc.queryForObject("SELECT email_verified FROM users WHERE user_id=?",Boolean.class,id));
        jdbc.update("UPDATE users SET email_delivery_state=jsonb_set(email_delivery_state,'{VERIFY_EMAIL,nextAttemptAt}','0') WHERE user_id=?",id);
        assertTrue(deliveries.pendingUsers().contains(id));
        String old=deliver(id);
        assertEquals("SENT",deliveryStatus(id,"VERIFY_EMAIL"));
        assertEquals(200,call(post("/api/auth/resend-verification"),null,Map.of("email","retry@example.test")).status);
        String latest=deliver(id);
        assertEquals(400,call(post("/api/auth/verify-email"),null,Map.of("email","retry@example.test","token",old)).status);
        assertEquals(200,call(post("/api/auth/verify-email"),null,Map.of("email","retry@example.test","token",latest)).status);
        assertNull(jdbc.queryForObject("SELECT email_delivery_state->'VERIFY_EMAIL'->>'encryptedToken' FROM users WHERE user_id=?",String.class,id));
    }

    @Test
    void resetIsOneTimeDoesNotEnumerateAndRevokesSessionsImmediately() throws Exception {
        int id=account("reset@example.test",true,null);
        String access=login("reset@example.test",password);
        var unknown=call(post("/api/auth/forgot-password"),null,Map.of("email","absent@example.test"));
        var known=call(post("/api/auth/forgot-password"),null,Map.of("email","reset@example.test"));
        assertEquals(unknown.json.path("message"),known.json.path("message"));
        assertEquals(200,call(get("/api/auth/me"),access,null).status); // requesting reset does not log out
        String reset=deliver(id);
        assertEquals(401,call(get("/api/auth/me"),reset,null).status);
        assertEquals(200,call(post("/api/auth/reset-password"),null,Map.of("email","reset@example.test","token",reset,"newPassword","NewPass@456")).status);
        assertEquals(400,call(post("/api/auth/reset-password"),null,Map.of("email","reset@example.test","token",reset,"newPassword","NewPass@789")).status);
        assertEquals(401,call(get("/api/auth/me"),access,null).status);
        String fresh=login("reset@example.test","NewPass@456");
        assertEquals(200,call(post("/api/auth/logout"),fresh,null).status);
        assertEquals(401,call(get("/api/auth/me"),fresh,null).status);
    }

    @Test
    void concurrentResetConsumesOneTokenOnce() throws Exception {
        int id=account("race@example.test",true,null);
        call(post("/api/auth/forgot-password"),null,Map.of("email","race@example.test"));
        String token=deliver(id);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> task=()->{ start.await(); return call(post("/api/auth/reset-password"),null,
                    Map.of("email","race@example.test","token",token,"newPassword","NewPass@456")).status; };
            var a=pool.submit(task); var b=pool.submit(task); start.countDown();
            assertEquals(List.of(200,400),java.util.stream.Stream.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT auth_version FROM users WHERE user_id=?",Long.class,id));
    }

    @Test
    void profileEditsKeepSessionAndAddressOwnershipAndDefaultAreEnforced() throws Exception {
        int a=account("a@example.test",true,null); int b=account("b@example.test",true,null);
        String token=login("a@example.test",password);
        int other=address(b,"Other");
        assertEquals(404,call(patch("/api/users/me/addresses/"+other),token,
                Map.of("recipientName","Attacker","addressLine","Wrong")).status);
        assertEquals(200,call(patch("/api/users/me"),token,Map.of("fullName","Updated","phone","0901234567")).status);
        assertEquals(200,call(get("/api/auth/me"),token,null).status);
        int first=address(a,"First");int second=address(a,"Second");
        assertEquals(200,call(post("/api/users/me/addresses/"+first+"/default"),token,null).status);
        assertEquals(200,call(post("/api/users/me/addresses/"+second+"/default"),token,null).status);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM user_addresses WHERE user_id=? AND is_default",Integer.class,a));
        assertEquals(403,call(get("/api/admin/users"),token,null).status);
    }

    @Test
    void bankReviewRequiresEvidenceAndFreshFingerprintAndPhoneInvalidatesVerification() throws Exception {
        int id=account("bank@example.test",true,null);String token=login("bank@example.test",password);
        var created=call(post("/api/users/me/bank-accounts"),token,bankBody("123456789012"));
        assertEquals(201,created.status);
        int bank=created.json.path("data").path("id").asInt();
        String fp=created.json.path("data").path("verificationFingerprint").asText();
        assertEquals(200,call(get("/api/admin/bank-accounts"),adminToken,null).status);
        assertFalse(created.json.path("data").path("accountNumberMasked").asText().equals("123456789012"));
        assertEquals(403,call(get("/api/admin/bank-accounts/"+bank+"/verification"),token,null).status);
        assertEquals(200,call(patch("/api/users/me/bank-accounts/"+bank),token,bankBody("999999999999")).status);
        assertEquals(400,call(patch("/api/admin/bank-accounts/"+bank+"/verification"),adminToken,
                Map.of("approved",true,"reason","Checked","evidenceReference","MOCK-1","accountFingerprint",fp)).status);
        var current=call(get("/api/users/me/bank-accounts"),token,null).json.path("data").get(0);
        assertEquals(200,call(patch("/api/admin/bank-accounts/"+bank+"/verification"),adminToken,
                Map.of("approved",true,"reason","Checked","evidenceReference","MOCK-2","accountFingerprint",current.path("verificationFingerprint").asText())).status);
        call(patch("/api/users/me"),token,Map.of("fullName","Changed Phone","phone","0911111111"));
        assertEquals("PENDING",jdbc.queryForObject("SELECT verification_status FROM user_bank_accounts WHERE bank_account_id=?",String.class,bank));
        assertFalse(jdbc.queryForObject("SELECT phone_verified FROM users WHERE user_id=?",Boolean.class,id));
    }

    @Test
    void sellerRejectResubmitApproveAndImportantEditRequiresReverification() throws Exception {
        int id=account("seller@example.test",true,null);String token=login("seller@example.test",password);
        int bank=jdbc.queryForObject("INSERT INTO user_bank_accounts(user_id,bank_code,bank_name,account_holder,account_number_token,account_number_masked,verification_status) VALUES (?,'MOCK','Mock','Seller','MOCK-TOKEN','******1234','VERIFIED') RETURNING bank_account_id",Integer.class,id);
        assertEquals(200,call(patch("/api/admin/users/"+id+"/phone-verification"),adminToken,
                Map.of("phone","0901234567","approved",true,"reason","Manual mock","evidenceReference","MOCK-PHONE")).status);
        var submitted=call(post("/api/users/me/seller-verification"),token,Map.of("bankAccountId",bank,"evidenceReference","DECLARATION-1"));
        assertEquals(200,call(get("/api/admin/seller-verifications"),adminToken,null).status);
        assertEquals(200,call(get("/api/admin/users"),adminToken,null).status);
        long at=submitted.json.path("data").path("verification").path("submittedAt").asLong();
        assertEquals(200,call(patch("/api/admin/users/"+id+"/seller-verification"),adminToken,
                Map.of("submittedAt",at,"approved",false,"reason","More evidence needed")).status);
        var again=call(post("/api/users/me/seller-verification"),token,Map.of("bankAccountId",bank,"evidenceReference","DECLARATION-2"));
        long next=again.json.path("data").path("verification").path("submittedAt").asLong();
        assertTrue(next>at);
        assertEquals(400,call(patch("/api/admin/users/"+id+"/seller-verification"),adminToken,
                Map.of("submittedAt",at,"approved",true,"reason","Stale decision")).status);
        var approved=call(patch("/api/admin/users/"+id+"/seller-verification"),adminToken,
                Map.of("submittedAt",next,"approved",true,"reason","Evidence checked"));
        assertTrue(approved.json.path("data").path("eligible").asBoolean());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM storefronts WHERE owner_user_id=?",Integer.class,id));
        assertEquals(400,call(put("/api/admin/users/"+id+"/roles/SELLER"),adminToken,
                Map.of("active",true,"reason","Must pay subscription first")).status);
        call(patch("/api/users/me"),token,Map.of("fullName","Seller","phone","0999999999"));
        assertFalse(call(get("/api/users/me/seller-verification"),token,null).json.path("data").path("eligible").asBoolean());
    }

    @Test
    void adminInternalAccountsLastAdminGuardSanctionsAndRoleRevocation() throws Exception {
        assertEquals(400,call(patch("/api/admin/users/"+admin+"/status"),adminToken,
                Map.of("status","BANNED","reason","Cannot ban last admin")).status);
        assertEquals(400,call(put("/api/admin/users/"+admin+"/roles/ADMIN"),adminToken,
                Map.of("active",false,"reason","Cannot remove last admin")).status);
        var created=call(post("/api/admin/users"),adminToken,Map.of("email","designer@example.test",
                "password",password,"fullName","Designer","role","DESIGNER"));
        assertEquals(201,created.status);
        int designer=created.json.path("data").path("id").asInt();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM storefronts WHERE owner_user_id=?",Integer.class,designer));
        int victim=account("victim@example.test",true,null);String token=login("victim@example.test",password);
        assertEquals(200,call(patch("/api/admin/users/"+victim+"/status"),adminToken,
                Map.of("status","BANNED","reason","Moderation decision")).status);
        assertEquals(401,call(get("/api/auth/me"),token,null).status);
        assertEquals(401,call(post("/api/auth/login"),null,Map.of("email","victim@example.test","password",password)).status);
        call(patch("/api/admin/users/"+victim+"/status"),adminToken,Map.of("status","ACTIVE","reason","Appeal approved"));
        String fresh=login("victim@example.test",password);
        assertEquals(200,call(put("/api/admin/users/"+victim+"/roles/PRINT_STAFF"),adminToken,
                Map.of("active",true,"reason","Staff assignment")).status);
        assertEquals(401,call(get("/api/auth/me"),fresh,null).status);
        String staff=login("victim@example.test",password);
        call(put("/api/admin/users/"+victim+"/roles/PRINT_STAFF"),adminToken,Map.of("active",false,"reason","Assignment ended"));
        assertEquals(401,call(get("/api/auth/me"),staff,null).status);
    }

    @Test
    void invalidAvatarDoesNotCallStorageOrChangeProfile() throws Exception {
        int id=account("avatar@example.test",true,null);String token=login("avatar@example.test",password);
        var bad=new MockMultipartFile("file","avatar.png","image/png","not an image".getBytes());
        var result=mvc.perform(multipart("/api/users/me/avatar").file(bad).header("Authorization","Bearer "+token)).andReturn();
        assertEquals(400,result.getResponse().getStatus());
        assertNull(jdbc.queryForObject("SELECT avatar_url FROM users WHERE user_id=?",String.class,id));
    }

    @Test
    void avatarStorageFailurePreservesOldUrlAndSuccessReplacesIt() throws Exception {
        int id=account("photo@example.test",true,null);String token=login("photo@example.test",password);
        jdbc.update("UPDATE users SET avatar_url='https://old.example.test/avatar.png',avatar_public_id='Avatar/old' WHERE user_id=?",id);
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var image=new MockMultipartFile("file","photo.png","image/png",bytes.toByteArray());
        when(storage.uploadAvatar(eq(id),any(byte[].class),eq("image/png")))
                .thenThrow(new com.project.w_3d_world.common.exception.ApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,"Storage unavailable"));
        assertEquals(502,mvc.perform(multipart("/api/users/me/avatar").file(image)
                .header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
        assertEquals("https://old.example.test/avatar.png",jdbc.queryForObject("SELECT avatar_url FROM users WHERE user_id=?",String.class,id));
        verify(storage,never()).delete(anyString());
        when(storage.uploadAvatar(eq(id),any(byte[].class),eq("image/png")))
                .thenReturn(new CloudinaryStorage.UploadedImage("https://res.cloudinary.com/demo/image/upload/Avatar/new.png","Avatar/new"));
        assertEquals(200,mvc.perform(multipart("/api/users/me/avatar").file(image)
                .header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus());
        verify(storage).delete("Avatar/old");
        assertEquals("https://res.cloudinary.com/demo/image/upload/Avatar/new.png",jdbc.queryForObject("SELECT avatar_url FROM users WHERE user_id=?",String.class,id));
        assertEquals(200,call(get("/api/users/me"),token,null).status);
    }

    @Test
    void concurrentAddressDefaultChangesKeepExactlyOneDefault() throws Exception {
        int id=account("defaults@example.test",true,null);String token=login("defaults@example.test",password);
        int a=address(id,"Address A");int b=address(id,"Address B");var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{gate.await();return call(post("/api/users/me/addresses/"+a+"/default"),token,null).status;});
            var second=pool.submit(()->{gate.await();return call(post("/api/users/me/addresses/"+b+"/default"),token,null).status;});
            gate.countDown();assertEquals(200,first.get(10,TimeUnit.SECONDS));assertEquals(200,second.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM user_addresses WHERE user_id=? AND is_default",Integer.class,id));
    }

    @Test
    void bannedSellerCannotSellNewItemsButStaffCanProcessExistingOrder() throws Exception {
        int seller=account("merchant@example.test",true,"SELLER");
        int staff=account("staff@example.test",true,"PRINT_STAFF");
        int buyer=account("buyer@example.test",true,null);
        String sellerToken=login("merchant@example.test",password);
        String staffToken=login("staff@example.test",password);
        int shop=jdbc.queryForObject("INSERT INTO storefronts(owner_user_id,storefront_name,status,seller_verification_status,expires_at) VALUES (?,'Shop','ACTIVE','VERIFIED',now()+interval '1 month') RETURNING storefront_id",Integer.class,seller);
        int product=jdbc.queryForObject("INSERT INTO products(storefront_id,title,status) VALUES (?,'Product','PUBLISHED') RETURNING product_id",Integer.class,shop);
        int variant=jdbc.queryForObject("INSERT INTO product_variants(product_id,sku,unit_price,stock_quantity,reserved_quantity) VALUES (?,'SKU',10000,5,1) RETURNING variant_id",Integer.class,product);
        int order=jdbc.queryForObject("INSERT INTO physical_orders(buyer_user_id,storefront_id,shipping_address_snapshot,subtotal,shipping_fee,total_amount,order_status,reservation_status) VALUES (?,?,'{}',10000,0,10000,'PAID','RESERVED') RETURNING physical_order_id",Integer.class,buyer,shop);
        jdbc.update("INSERT INTO physical_order_items(physical_order_id,variant_id,sku_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,?,'SKU',1,10000,10000)",order,variant);
        jdbc.update("INSERT INTO transactions(transaction_type,purpose,operation_key,physical_order_id,amount,settlement_mode,status) VALUES ('PAYMENT','PHYSICAL_ORDER','existing-paid',?,10000,'PLATFORM','SUCCESS')",order);
        call(patch("/api/admin/users/"+seller+"/status"),adminToken,Map.of("status","BANNED","reason","Account sanction"));
        assertEquals(401,call(get("/api/orders/seller"),sellerToken,null).status);
        assertEquals(200,call(patch("/api/orders/"+order+"/status"),staffToken,Map.of("status","PROCESSING")).status);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM transactions WHERE physical_order_id=?",Integer.class,order));
        assertEquals(4,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE variant_id=?",Integer.class,variant));
        var storefront=context.getBean(com.project.w_3d_world.features.storefront.repository.StorefrontRepository.class);
        var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        Boolean available=tx.execute(status->storefront.findById(shop).orElseThrow().canPublish());
        assertEquals(Boolean.FALSE,available);
    }


    @Test
    void concurrentAdminDemotionsCannotRemoveTheLastAdmin() throws Exception {
        int second=account("second-admin@example.test",true,"ADMIN");
        String secondToken=login("second-admin@example.test",password);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{gate.await();return call(put("/api/admin/users/"+admin+"/roles/ADMIN"),
                    adminToken,Map.of("active",false,"reason","Self demotion")).status;});
            var other=pool.submit(()->{gate.await();return call(put("/api/admin/users/"+second+"/roles/ADMIN"),
                    secondToken,Map.of("active",false,"reason","Self demotion")).status;});
            gate.countDown();
            assertEquals(List.of(200,400),java.util.stream.Stream.of(first.get(10,TimeUnit.SECONDS),other.get(10,TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM user_roles ur JOIN roles r USING(role_id) JOIN users u USING(user_id) WHERE r.role_code='ADMIN' AND ur.approval_status='ACTIVE' AND u.account_status='ACTIVE'",Integer.class));
    }

    int account(String email,boolean verified,String extra) {
        int id=jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,phone,email_verified) VALUES (?,?,'Test Account','0901234567',?) RETURNING user_id",Integer.class,email,passwords.encode(password),verified);
        jdbc.update("INSERT INTO user_roles(user_id,role_id,approval_status) SELECT ?,role_id,'ACTIVE' FROM roles WHERE role_code='CUSTOMER'",id);
        if(extra!=null)jdbc.update("INSERT INTO user_roles(user_id,role_id,approval_status) SELECT ?,role_id,'ACTIVE' FROM roles WHERE role_code=?",id,extra);
        return id;
    }
    int register(String email) throws Exception {
        var result=call(post("/api/auth/register"),null,Map.of("email",email,"password",password,"fullName","Test Account"));
        assertEquals(201,result.status,result.json.toString());return result.json.path("data").path("userId").asInt();
    }
    int address(int owner,String text) {
        return jdbc.queryForObject("INSERT INTO user_addresses(user_id,recipient_name,address_line) VALUES (?,'Test',?) RETURNING address_id",Integer.class,owner,text);
    }
    Map<String,Object> bankBody(String number) {return Map.of("bankCode","MOCK","bankName","Mock Bank","accountHolder","TEST USER","accountNumber",number);}
    String login(String email,String pass) throws Exception {
        var result=call(post("/api/auth/login"),null,Map.of("email",email,"password",pass));
        assertEquals(200,result.status,result.json.toString());return result.json.path("data").path("accessToken").asText(result.json.path("data").path("token").asText());
    }
    String deliver(int id) {
        deliveries.dispatch(id);
        var link=ArgumentCaptor.forClass(String.class);
        verify(email,atLeastOnce()).sendLink(anyString(),anyString(),anyString(),link.capture());
        String url=link.getAllValues().getLast();
        return URLDecoder.decode(url.substring(url.indexOf("token=")+6),StandardCharsets.UTF_8);
    }
    String deliveryStatus(int id,String purpose) {return jdbc.queryForObject("SELECT email_delivery_state->?->>'status' FROM users WHERE user_id=?",String.class,purpose,id);}
    Result call(MockHttpServletRequestBuilder request,String token,Object body) throws Exception {
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(request).andReturn().getResponse();
        return new Result(response.getStatus(),mapper.readTree(response.getContentAsString()));
    }
    record Result(int status,JsonNode json) {}
}
