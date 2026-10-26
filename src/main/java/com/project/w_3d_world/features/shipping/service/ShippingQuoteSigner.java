package com.project.w_3d_world.features.shipping.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.shipping.dto.ShippingQuoteDtos.SignedQuote;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ShippingQuoteSigner {
    private final byte[] key;
    private final ObjectMapper mapper;
    public ShippingQuoteSigner(@Value("${app.jwt.secret-key}") String secret, ObjectMapper mapper) {
        this.key = Base64.getDecoder().decode(secret);
        this.mapper = mapper;
    }
    public String sign(SignedQuote quote) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mapper.writeValueAsString(quote).getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(payload));
    }
    public SignedQuote read(String token) {
        try {
            if (token == null || token.length() > 500000) throw new IllegalArgumentException();
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !MessageDigest.isEqual(mac(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
                throw new IllegalArgumentException();
            }
            SignedQuote quote = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), SignedQuote.class);
            if (quote.expiresAt() <= System.currentTimeMillis()) throw new IllegalArgumentException();
            return quote;
        } catch (RuntimeException exception) {
            throw ApiException.invalidRequest("Shipping quote is invalid or expired. Request a new shipping quote.");
        }
    }
    private byte[] mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(("3DWorld:shipping-quote:v1:" + value).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) { throw new IllegalStateException("Cannot sign shipping quote."); }
    }
}
