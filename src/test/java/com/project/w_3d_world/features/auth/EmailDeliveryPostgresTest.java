package com.project.w_3d_world.features.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.project.w_3d_world.W3dWorldApplication;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.payment.service.PaymentService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named = "email.pg.integration", matches = "true")
@SpringBootTest(classes = W3dWorldApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/marketplacecheck",
        "spring.datasource.username=canhan7325", "spring.datasource.password=",
        "spring.sql.init.mode=never", "app.auth.email-worker-enabled=true",
        "app.auth.email-resend-cooldown-seconds=0"
})
class EmailDeliveryPostgresTest {
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean EmailService email;
    @MockitoBean CloudinaryStorage cloudinary;
    @MockitoBean PaymentService payments;

    @Test
    void registrationDispatchesAfterCommitAndResendActuallyDeliversAgain() throws Exception {
        jdbc.update("INSERT INTO roles(role_code,role_name) VALUES ('CUSTOMER','Customer') ON CONFLICT (role_code) DO NOTHING");
        String recipient = "verify-" + UUID.randomUUID() + "@example.com";
        BlockingQueue<String> links = new LinkedBlockingQueue<>();
        doAnswer(invocation -> {
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, recipient));
            links.add(invocation.getArgument(3));
            return null;
        }).when(email).sendLink(eq(recipient), anyString(), anyString(), anyString());
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", recipient, "password", "Admin@123",
                        "fullName", "Email Buyer", "phone", "0901234567")))).andReturn();
        assertEquals(201, registered.getResponse().getStatus(), registered.getResponse().getContentAsString());
        String first = links.poll(5, TimeUnit.SECONDS);
        assertNotNull(first, "Registration must trigger delivery without waiting for the retry poll.");
        var resend = mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", recipient)))).andReturn();
        assertEquals(200, resend.getResponse().getStatus());
        assertEquals("0", resend.getResponse().getHeader("Retry-After"));
        String second = links.poll(5, TimeUnit.SECONDS);
        assertNotNull(second, "Resend must deliver another email.");
        var oldLink = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", recipient, "token", token(first))))).andReturn();
        assertEquals(400, oldLink.getResponse().getStatus());
        var verified = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", recipient, "token", token(second))))).andReturn();
        assertEquals(200, verified.getResponse().getStatus(), verified.getResponse().getContentAsString());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT email_verified FROM users WHERE email=?", Boolean.class, recipient));
    }

    @Test
    void emailLinksServeHtmlOverRealHttpWithoutAuthentication() throws Exception {
        String port = environment.getRequiredProperty("local.server.port");
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            for (String page : List.of("verify-email", "reset-password")) {
                var request = java.net.http.HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/" + page + "?email=buyer%40example.com&token=example"))
                        .GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
                assertTrue(response.body().contains("<!doctype html>"));
                assertTrue(response.body().contains("/api/auth/" + page));
            }
        }
    }

    private String token(String link) {
        return Arrays.stream(URI.create(link).getRawQuery().split("&")).filter(value -> value.startsWith("token="))
                .findFirst().map(value -> URLDecoder.decode(value.substring(6), StandardCharsets.UTF_8)).orElseThrow();
    }
}
