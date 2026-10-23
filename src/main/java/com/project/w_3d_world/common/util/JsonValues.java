package com.project.w_3d_world.common.util;

import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public final class JsonValues {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonValues() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static String normalizeMetadata(String value) {
        if (value == null) {
            return null;
        }
        try {
            MAPPER.readTree(value);
            return value;
        } catch (RuntimeException exception) {
            return write(Map.of("detail", value));
        }
    }
}
