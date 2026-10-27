package com.project.w_3d_world.features.ai.util;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads vertex count, triangle count, and bounding box from GLB without external 3D engine libraries.
 */
public final class GlbStats {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GlbStats() {}

    public static Map<String, Object> of(byte[] buf) {
        try {
            if (buf == null || buf.length < 20) return null;
            ByteBuffer b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt(0) != 0x46546c67) return null;
            int jsonLen = b.getInt(12);
            if (20 + jsonLen > buf.length) return null;
            JsonNode gltf = MAPPER.readTree(Arrays.copyOfRange(buf, 20, 20 + jsonLen));
            long vertices = 0;
            double triangles = 0;
            double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            JsonNode acc = gltf.path("accessors");
            for (JsonNode mesh : gltf.path("meshes")) {
                for (JsonNode p : mesh.path("primitives")) {
                    JsonNode pa = acc.get(p.path("attributes").path("POSITION").asInt());
                    if (pa != null) {
                        long count = pa.path("count").asLong();
                        vertices += count;
                        if (pa.has("min") && pa.has("max")) {
                            for (int k = 0; k < 3; k++) {
                                min[k] = Math.min(min[k], pa.get("min").get(k).asDouble());
                                max[k] = Math.max(max[k], pa.get("max").get(k).asDouble());
                            }
                        }
                        triangles += p.hasNonNull("indices")
                                ? acc.get(p.get("indices").asInt()).path("count").asLong() / 3.0
                                : count / 3.0;
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("vertices", vertices);
            out.put("triangles", Math.round(triangles));
            if (min[0] == Double.MAX_VALUE) {
                out.put("size", null);
            } else {
                out.put("size", new double[]{
                        round4(max[0] - min[0]),
                        round4(max[1] - min[1]),
                        round4(max[2] - min[2])
                });
            }
            out.put("materials", gltf.path("materials").size());
            out.put("textures", gltf.path("images").size());
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
