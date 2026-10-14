package com.project.w_3d_world.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Component
public class EmailTokenCodec {
    private final SecureRandom random = new SecureRandom();
    private final SecretKey encryptionKey;

    public EmailTokenCodec(@Value("${app.auth.token-encryption-key}") String secret) {
        byte[] key = Base64.getDecoder().decode(secret);
        if (key.length != 32) {
            throw new IllegalArgumentException(
                    "EMAIL_TOKEN_ENCRYPTION_KEY must contain 32 random bytes encoded as Base64");
        }
        encryptionKey = new SecretKeySpec(key, "AES");
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot hash email token", exception);
        }
    }

    // The encrypted copy exists only while delivery/retry is pending.
    public String encrypt(String token, String reference) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot encrypt email token", exception);
        }
    }

    public String decrypt(String encryptedToken, String reference) {
        try {
            byte[] data = Base64.getDecoder().decode(encryptedToken);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    encryptionKey,
                    new GCMParameterSpec(128, Arrays.copyOfRange(data, 0, 12)));
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            return new String(
                    cipher.doFinal(Arrays.copyOfRange(data, 12, data.length)),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot decrypt email token", exception);
        }
    }
}
