package com.project.w_3d_world.features.auth;

import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.delivery.EmailTokenEncryption;
import com.project.w_3d_world.features.auth.enums.TokenPurpose;
import com.project.w_3d_world.features.auth.service.impl.AccountValidation;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class AccountTokenTests {
    private final byte[] key = new byte[32];
    private final String secret = Base64.getEncoder().encodeToString(key);
    private final JwtService service = new JwtService(secret, 60000);

    @Test
    void tokenPurposeIsolationAndImmediateValidity() {
        String access = service.generateToken(1, Set.of("CUSTOMER"), "", "test@example.test", 7).getTokenValue();
        String verify = service.generateEmailToken(1, "test@example.test", TokenPurpose.VERIFY_EMAIL, 60000, 3).getTokenValue();
        String reset = service.generateEmailToken(1, "test@example.test", TokenPurpose.RESET_PASSWORD, 60000, 5).getTokenValue();
        assertEquals(7L, UUID.fromString(service.validateToken(access).getId()).getMostSignificantBits());
        assertEquals(3L, ((Number) service.validateEmailToken(verify, TokenPurpose.VERIFY_EMAIL).getClaim("emailVersion")).longValue());
        assertThrows(JwtException.class, () -> service.validateToken(verify));
        assertThrows(JwtException.class, () -> service.validateToken(reset));
        assertThrows(JwtException.class, () -> service.validateEmailToken(access, TokenPurpose.RESET_PASSWORD));
        assertThrows(JwtException.class, () -> service.validateEmailToken(verify, TokenPurpose.RESET_PASSWORD));
    }

    @Test
    void rejectsExpiredEmailTokenAndMismatchedSessionVersion() {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
        var claims = JwtClaimsSet.builder().subject("1").id(UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60))
                .claim("email", "test@example.test").claim("purpose", "VERIFY_EMAIL").claim("emailVersion", 1).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThrows(JwtException.class, () -> service.validateEmailToken(expired, TokenPurpose.VERIFY_EMAIL));
        var wrongVersion = JwtClaimsSet.builder().subject("1").id(new java.util.UUID(99, 7).toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("purpose", "ACCESS").claim("authVersion", 100).build();
        String mismatched = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), wrongVersion)).getTokenValue();
        assertThrows(JwtException.class, () -> service.validateToken(mismatched));
    }

    @Test
    void encryptedDeliveryTokenCannotBeReadOrMovedToAnotherReference() throws Exception {
        var encryption = new EmailTokenEncryption(secret);
        String token = service.generateEmailToken(1, "test@example.test", TokenPurpose.VERIFY_EMAIL, 60000, 1).getTokenValue();
        String encrypted = encryption.encrypt(token, "reference-a");
        assertFalse(encrypted.contains(token));
        assertEquals(token, encryption.decrypt(encrypted, "reference-a"));
        assertThrows(IllegalStateException.class, () -> encryption.decrypt(encrypted, "reference-b"));
        byte[] raw = Base64.getDecoder().decode(encrypted); raw[raw.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> encryption.decrypt(Base64.getEncoder().encodeToString(raw), "reference-a"));
    }

    @Test
    void passwordPolicyChecksUtf8LimitAndAllCharacterGroups() {
        assertDoesNotThrow(() -> AccountValidation.password("Admin@123"));
        assertThrows(RuntimeException.class, () -> AccountValidation.password("password"));
        assertThrows(RuntimeException.class, () -> AccountValidation.password("A1@" + "ế".repeat(30)));
        assertThrows(RuntimeException.class, () -> AccountValidation.bankNumber("123"));
    }
}
