package com.project.w_3d_world.common.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expiration;

    public JwtService(@Value("${app.jwt.secret-key}") String secretKey,
                      @Value("${app.jwt.expiration}") long expiration) {
        byte[] secret = Base64.getDecoder().decode(secretKey);
        if (secret.length < 32 || expiration <= 0) {
            throw new IllegalArgumentException(
                    "JWT requires a Base64 secret of at least 32 bytes and positive expiration.");
        }
        var key = new SecretKeySpec(secret, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        decoder = jwtDecoder;
        this.expiration = expiration;
    }

    public Jwt generateToken(int userId, Set<String> roles, String phone, String email) {
        return generateToken(userId, roles, phone, email, 0);
    }

    public Jwt generateToken(int userId, Set<String> roles, String phone, String email, long authVersion) {
        Instant now = Instant.now();
        // Signed jti carries the account epoch while retaining a random session identifier.
        UUID session = new UUID(authVersion, UUID.randomUUID().getLeastSignificantBits());
        return encode(JwtClaimsSet.builder().id(session.toString()).issuedAt(now)
                .subject(Integer.toString(userId)).expiresAt(now.plusMillis(expiration))
                .claim("purpose", "ACCESS").claim("authVersion", authVersion)
                .claim("roles", new ArrayList<>(roles)).claim("email", email)
                .claim("phone", Objects.requireNonNullElse(phone, "")).build());
    }

    public Jwt validateToken(String token) {
        Jwt jwt = decode(token);
        if (!"ACCESS".equals(jwt.getClaimAsString("purpose"))
                || !(jwt.getClaim("authVersion") instanceof Number version)
                || version.longValue()
                    != UUID.fromString(jwt.getId()).getMostSignificantBits()) {
            throw new JwtException("Invalid access token purpose or account version.");
        }
        return jwt;
    }

    public Jwt generateEmailToken(int userId, String email, TokenPurpose purpose, long ttlMillis) {
        return generateEmailToken(userId, email, purpose, ttlMillis, 0);
    }

    public Jwt generateEmailToken(int userId, String email, TokenPurpose purpose, long ttlMillis, long version) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("Email token expiration must be positive.");
        }
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder().id(UUID.randomUUID().toString()).issuedAt(now)
                .subject(Integer.toString(userId)).expiresAt(now.plusMillis(ttlMillis))
                .claim("email", email).claim("purpose", purpose.name()).claim("emailVersion", version).build());
    }

    public Jwt validateEmailToken(String token, TokenPurpose purpose) {
        Jwt jwt = decode(token);
        if (!purpose.name().equals(jwt.getClaimAsString("purpose"))
                || jwt.getClaimAsString("email") == null || jwt.getClaim("emailVersion") == null) {
            throw new JwtException("Invalid email token purpose.");
        }
        return jwt;
    }

    private Jwt decode(String token) {
        Jwt jwt = decoder.decode(token);
        if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getId() == null
                || jwt.getSubject() == null || jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(1))) {
            throw new JwtException("Required token claims are missing or invalid.");
        }
        return jwt;
    }

    private Jwt encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    }
}
