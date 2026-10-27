package com.project.w_3d_world.features.ai.service;

import java.util.*;

/**
 * Service contract for AI credit pricing, model options, and configuration normalization.
 */
public interface PricingService {
    String PRICE_VERSION = "pv-2026-10-22";

    record Model(String label, int meshOnly, Map<String, Integer> tex, List<String> geo) {}

    Map<String, Model> MODELS = initModels();
    Map<String, Map<String, String>> PRESETS = initPresets();

    private static Map<String, Integer> ordered(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Model> initModels() {
        Map<String, Model> m = new LinkedHashMap<>();
        m.put("latest", new Model("Meshy 7.1 · Latest", 20, ordered("2k", 30, "4k", 30, "8k", 35), List.of("standard", "2k", "4k")));
        m.put("meshy-6", new Model("Meshy 6", 20, ordered("2k", 30, "4k", 30, "8k", 35), List.of("standard")));
        m.put("meshy-6-lite", new Model("Meshy 6 Lite · Fast", 5, ordered("2k", 15), List.of("standard")));
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Map<String, String>> initPresets() {
        Map<String, Map<String, String>> p = new LinkedHashMap<>();
        p.put("fast", Map.of("aiModel", "meshy-6-lite", "textureResolution", "2k", "geometryResolution", "standard"));
        p.put("balanced", Map.of("aiModel", "latest", "textureResolution", "2k", "geometryResolution", "standard"));
        p.put("high", Map.of("aiModel", "latest", "textureResolution", "4k", "geometryResolution", "2k"));
        return Collections.unmodifiableMap(p);
    }

    Map<String, Object> normalizeConfig(Map<String, Object> input, String mode);

    Map<String, Object> estimate(Map<String, Object> cfg, int variants);
}
