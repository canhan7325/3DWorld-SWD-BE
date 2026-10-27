package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.service.PricingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implementation of AI credit pricing, model options, and configuration normalization.
 */
@Service
public class PricingServiceImpl implements PricingService {
    private static final List<String> FORMATS = List.of("glb", "obj", "fbx", "stl", "usdz", "3mf");

    private static String str(Map<String, Object> in, String k, String def) {
        Object v = in.get(k);
        return v == null ? def : v.toString();
    }

    @Override
    public Map<String, Object> normalizeConfig(Map<String, Object> input, String mode) {
        if (input == null) input = Map.of();
        String aiModel = MODELS.containsKey(str(input, "aiModel", "")) ? str(input, "aiModel", "") : "latest";
        Model m = MODELS.get(aiModel);
        boolean shouldTexture = !Boolean.FALSE.equals(input.get("shouldTexture"));
        String textureResolution = shouldTexture ? str(input, "textureResolution", "2k") : "2k";
        if (textureResolution.isBlank()) textureResolution = "2k";
        if (shouldTexture && !m.tex().containsKey(textureResolution)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Texture resolution " + textureResolution + " is not supported with " + m.label());
        }
        String geometryResolution = str(input, "geometryResolution", "standard");
        if (geometryResolution.isBlank()) geometryResolution = "standard";
        if (!m.geo().contains(geometryResolution)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geometry resolution " + geometryResolution + " is not supported with " + m.label());
        }
        boolean shouldRemesh = Boolean.TRUE.equals(input.get("shouldRemesh"));
        long targetPolycount = 30000;
        Object tp = input.get("targetPolycount");
        if (tp instanceof Number n) targetPolycount = Math.round(n.doubleValue());
        else if (tp instanceof String s && !s.isBlank()) {
            try { targetPolycount = Math.round(Double.parseDouble(s)); } catch (NumberFormatException ignored) {}
        }
        if (targetPolycount <= 0) targetPolycount = 30000;
        if (shouldRemesh && (targetPolycount < 100 || targetPolycount > 300000)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Polycount must be between 100 and 300,000.");
        }
        String topology = "quad".equalsIgnoreCase(str(input, "topology", "")) ? "quad" : "triangle";
        String poseMode = List.of("a-pose", "t-pose").contains(str(input, "poseMode", "")) ? str(input, "poseMode", "") : "";
        String texturePrompt = str(input, "texturePrompt", "");
        if (texturePrompt.length() > 800) texturePrompt = texturePrompt.substring(0, 800);

        List<String> targetFormats = new ArrayList<>();
        if (input.get("targetFormats") instanceof List<?> l) {
            for (Object f : l) {
                if (f != null && FORMATS.contains(f.toString()) && !targetFormats.contains(f.toString())) {
                    targetFormats.add(f.toString());
                }
            }
        }
        if (!targetFormats.contains("glb")) targetFormats.add(0, "glb");
        boolean lite = "meshy-6-lite".equals(aiModel);

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("aiModel", aiModel);
        cfg.put("shouldTexture", shouldTexture);
        cfg.put("enablePbr", shouldTexture && Boolean.TRUE.equals(input.get("enablePbr")));
        cfg.put("textureResolution", textureResolution);
        cfg.put("texturePrompt", shouldTexture ? texturePrompt : "");
        cfg.put("geometryResolution", geometryResolution);
        cfg.put("shouldRemesh", shouldRemesh);
        cfg.put("topology", topology);
        cfg.put("targetPolycount", targetPolycount);
        cfg.put("poseMode", poseMode);
        cfg.put("imageEnhancement", !Boolean.FALSE.equals(input.get("imageEnhancement")) && !lite);
        cfg.put("removeLighting", !Boolean.FALSE.equals(input.get("removeLighting")) && !lite);
        cfg.put("targetFormats", targetFormats);
        return cfg;
    }

    @Override
    public Map<String, Object> estimate(Map<String, Object> cfg, int variants) {
        if (cfg == null) cfg = Map.of();
        String aiModel = str(cfg, "aiModel", "latest");
        Model m = MODELS.getOrDefault(aiModel, MODELS.get("latest"));
        int cost = Boolean.TRUE.equals(cfg.get("shouldTexture"))
                ? m.tex().getOrDefault(str(cfg, "textureResolution", "2k"), 30)
                : m.meshOnly();
        int count = Math.max(1, Math.min(3, variants));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("perVariant", cost);
        res.put("variants", count);
        res.put("total", cost * count);
        res.put("priceVersion", PRICE_VERSION);
        return res;
    }
}
