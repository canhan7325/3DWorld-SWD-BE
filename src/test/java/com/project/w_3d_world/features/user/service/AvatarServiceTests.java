package com.project.w_3d_world.features.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.project.w_3d_world.common.exception.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AvatarServiceTests {
    private final AvatarImageValidator service = new AvatarImageValidator();

    @Test
    void rejectsTextDisguisedAsImage() {
        var file = new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes());
        assertThrows(ApiException.class, () -> service.validateAndNormalize(file));
    }

    @Test
    void rejectsEmptyAndOversizedUploads() {
        assertThrows(ApiException.class, () -> service.validateAndNormalize(
                new MockMultipartFile("file", new byte[0])));
        assertThrows(ApiException.class, () -> service.validateAndNormalize(
                new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1])));
    }

    @Test
    void decodesRealImageAndNormalizesToPng() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        var file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", bytes.toByteArray());
        byte[] result = service.validateAndNormalize(file);
        var decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertNotNull(decoded);
        assertEquals(12, decoded.getWidth());
        assertEquals(8, decoded.getHeight());
        assertEquals(0x89, Byte.toUnsignedInt(result[0]));
        assertEquals('P', result[1]);
    }
}
