package com.project.w_3d_world.common.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expiration;

    public JwtService(
            @Value("${app.jwt.secret-key}") String secretKey,
            @Value("${app.jwt.expiration}") long expiration) {
        byte[] secret = Base64.getDecoder().decode(secretKey);
        if (secret.length < 32 || expiration <= 0) {
            throw new IllegalArgumentException(
                    "JWT requires a Base64 secret of at least 32 bytes and positive expiration.");
        }
        var key = new SecretKeySpec(secret, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var jwtDecoder =
                NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        decoder = jwtDecoder;
        this.expiration = expiration;
    }

    public Jwt generateToken(int userId, Set<String> roles, String phone, String email) {
        var claims =
                JwtClaimsSet.builder()
                        .subject(Integer.toString(userId))
                        .expiresAt(Instant.now().plusMillis(expiration))
                        .claim("userId", userId)
                        .claim("roles", new ArrayList<>(roles))
                        .claim("phone", Objects.requireNonNullElse(phone, ""))
                        .claim("email", email)
                        .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims));
    }

    public Jwt validateToken(String token) {
        Jwt jwt = decoder.decode(token);
        if (jwt.getExpiresAt() == null) {
            throw new JwtException("Token expiration is required.");
        }
        return jwt;
    }
}
