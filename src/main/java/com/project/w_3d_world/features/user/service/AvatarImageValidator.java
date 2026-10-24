package com.project.w_3d_world.features.user.service;

import com.project.w_3d_world.common.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AvatarImageValidator {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;

    public byte[] validateAndNormalize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw ApiException.invalidRequest("Upload a non-empty JPEG or PNG image up to 5 MB.");
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw ApiException.invalidRequest("The file is not a valid JPEG or PNG image.");
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("jpeg") || format.equals("png"))) {
                    throw ApiException.invalidRequest("Only JPEG and PNG images are supported.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw ApiException.invalidRequest("Image must contain at most 16 million pixels.");
                }
                var decoded = reader.read(0);
                var output = new ByteArrayOutputStream();
                ImageIO.write(decoded, "png", output);
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw ApiException.invalidRequest("Image data is corrupt or cannot be decoded.");
        }
    }
}
