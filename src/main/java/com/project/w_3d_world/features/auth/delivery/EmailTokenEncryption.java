package com.project.w_3d_world.features.auth.delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailTokenEncryption {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EmailTokenEncryption(@Value("${app.jwt.secret-key}") String secret) throws Exception {
        byte[] raw = Base64.getDecoder().decode(secret);
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update("3DWorld:email-delivery:v1:".getBytes(StandardCharsets.UTF_8));
        key = new SecretKeySpec(digest.digest(raw), "AES");
    }

    public String encrypt(String value, String reference) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            byte[] payload = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(iv, iv.length + payload.length);
            System.arraycopy(payload, 0, result, iv.length, payload.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot encrypt email token");
        }
    }

    public String decrypt(String value, String reference) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot decrypt email token");
        }
    }
}
