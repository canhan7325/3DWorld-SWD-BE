package com.project.w_3d_world.features.shipping.ghtk;

import com.project.w_3d_world.common.exception.ApiException;
import java.math.BigDecimal;
import java.net.http.*;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GhtkClient {
    private final GhtkProperties config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record Address(String address, String province, String district, String ward) {}
    public record Fee(BigDecimal shipping, BigDecimal insurance, String service, String extraFees,
                      Boolean delivery) {
        public Fee(BigDecimal shipping, BigDecimal insurance, String service, String extraFees) {
            this(shipping, insurance, service, extraFees, null);
        }

        public BigDecimal total() { return shipping.add(insurance); }
    }

    public Fee quote(Address pickup, Address destination, int weightGrams, BigDecimal value, boolean fragile) {
        if (!config.enabled() || config.token() == null || config.token().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GHTK fee service is not configured.");
        }
        if (weightGrams < 1) throw ApiException.invalidRequest("Package weight must be positive.");
        var uri = UriComponentsBuilder.fromUriString(config.baseUrl()).path("/services/shipment/fee")
                .queryParam("pick_address", pickup.address()).queryParam("pick_province", pickup.province())
                .queryParam("pick_ward", pickup.ward())
                .queryParam("address", destination.address()).queryParam("province", destination.province())
                .queryParam("ward", destination.ward())
                .queryParam("weight", weightGrams).queryParam("value", value.longValueExact())
                .queryParam("transport", config.transport());
        if (fragile) uri.queryParam("tags[]", 1);
        var builder = HttpRequest.newBuilder(uri.build().encode().toUri()).timeout(Duration.ofSeconds(10))
                .header("Token", config.token()).GET();
        if (config.partnerCode() != null && !config.partnerCode().isBlank()) {
            builder.header("X-Client-Source", config.partnerCode());
        }
        try {
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw unavailable();
            var body = mapper.readTree(response.body());
            if (!body.path("success").asBoolean()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "GHTK could not calculate a fee for these addresses. Please check the addresses and try again.");
            }
            var fee = body.path("fee");
            if (!fee.path("fee").isNumber() || !fee.path("insurance_fee").isNumber()) throw unavailable();
            var shipping = fee.path("fee").decimalValue();
            var insurance = fee.path("insurance_fee").decimalValue();
            if (shipping.signum() < 0 || insurance.signum() < 0) throw unavailable();
            // extFees describe carrier surcharges; do not add them twice to the quoted fee.
            // Fee-only integration: delivery is informational, never a checkout eligibility gate.
            Boolean delivery = fee.path("delivery").isBoolean() ? fee.path("delivery").asBoolean() : null;
            return new Fee(shipping, insurance, fee.path("name").asText(), fee.path("extFees").toString(), delivery);
        } catch (ApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        // Never log/return the API token or raw provider response.
        return new ApiException(HttpStatus.BAD_GATEWAY, "GHTK fee service is unavailable. Please request a new quote.");
    }
}
