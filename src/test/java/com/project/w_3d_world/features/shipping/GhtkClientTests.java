package com.project.w_3d_world.features.shipping;

import static org.junit.jupiter.api.Assertions.*;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.shipping.ghtk.*;
import com.project.w_3d_world.features.shipping.ghtk.GhtkClient.Address;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GhtkClientTests {
    @Test
    void sendsGramWeightAndTokenParsesFeesAndRejectsUndeliverableRoute() throws Exception {
        var query = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var response = new AtomicReference<>("""
                {"success":true,"fee":{"name":"area1","fee":30400,"insurance_fee":15000,
                "delivery":true,"extFees":[{"amount":7400,"type":"fragile"}]}}
                """);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/services/shipment/fee", exchange -> {
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("Token"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var config = new GhtkProperties(true, "test-token", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-partner", "road", new BigDecimal("2"), 300);
            var client = new GhtkClient(config, new ObjectMapper());
            Address address = new Address("1 Main Street", "Hà Nội", "", "Test ward");
            var fee = client.quote(address, address, 1250, new BigDecimal("100000"), true);
            assertEquals("test-token", token.get());
            assertTrue(query.get().contains("weight=1250"));
            assertTrue(query.get().contains("pick_ward=Test ward"));
            assertTrue(query.get().contains("tags[]=1"));
            assertEquals(new BigDecimal("45400"), fee.total());
            response.set("{\"success\":true,\"fee\":{\"delivery\":false,\"fee\":0,\"insurance_fee\":0}}");
            assertThrows(ApiException.class, () -> client.quote(address, address, 1250, BigDecimal.ZERO, false));
        } finally { server.stop(0); }
    }
}
