package com.project.w_3d_world.features.studio.util;

import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class StudioSceneHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_SCENE_SIZE = 5_000_000; // 5 MB

    private StudioSceneHelper() {}

    public static byte[] serializeScene(Object scene) {
        if (scene == null) {
            return "{\"nodes\":[]}".getBytes(StandardCharsets.UTF_8);
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(scene);
            if (bytes.length > MAX_SCENE_SIZE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Scene graph exceeds maximum size limit of 5 MB.");
            }
            return bytes;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid scene JSON: " + e.getMessage());
        }
    }

    public static Object parseScene(InputStream input) {
        if (input == null) return Map.of("nodes", List.of());
        try {
            return MAPPER.readTree(input);
        } catch (Exception e) {
            return Map.of("nodes", List.of());
        }
    }
}
