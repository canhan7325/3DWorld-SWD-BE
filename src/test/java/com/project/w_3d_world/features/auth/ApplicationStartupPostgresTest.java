package com.project.w_3d_world.features.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.project.w_3d_world.W3dWorldApplication;
import com.project.w_3d_world.common.service.EmailService;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.payment.service.PaymentService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Boots the actual application against an explicitly disposable local schema. */
@EnabledIfSystemProperty(named = "auth.pg.integration", matches = "true")
@SpringBootTest(classes = W3dWorldApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/authcheck",
            "spring.datasource.username=canhan7325",
            "spring.datasource.password=",
            "spring.sql.init.mode=never",
            "app.auth.email-worker-enabled=false"
        })
class ApplicationStartupPostgresTest {

    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService email;
    @MockitoBean CloudinaryStorage cloudinary;
    @MockitoBean PaymentService payments;

    @Test
    void startsWithoutFlywayHistoryAndPublishesAccountSwagger() throws Exception {
        assertEquals("false", environment.getProperty("spring.flyway.enabled"));
        assertNull(jdbc.queryForObject(
                "select to_regclass('public.flyway_schema_history')::text", String.class));

        String port = environment.getRequiredProperty("local.server.port");
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            for (String path : new String[] {
                    "/api/auth/register", "/api/auth/reset-password", "/api/users/me/avatar",
                    "/api/admin/users", "/api/users/me/seller-verification",
                    "/api/admin/bank-accounts/{id}/verification"
            }) {
                assertTrue(response.body().contains(path), "Missing Swagger path: " + path);
            }
        }
    }
}
