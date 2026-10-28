package com.project.w_3d_world.features.ai.util;

import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates procedural 3D models (GLB + STL) for testing and mock mode without calling Meshy.
 */
public final class Procedural {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Procedural() {}

    private record Geo(float[] pos, float[] nrm, int[] idx, double[] color) {}

    private static final class Rng {
        private int h;

        Rng(String seed) {
            h = (int) 2166136261L;
            for (int i = 0; i < seed.length(); i++) {
                h = (h ^ seed.charAt(i)) * 16777619;
            }
        }

        double next() {
            h = (h ^ (h >>> 15)) * (int) 2246822507L;
            h = (h ^ (h >>> 13)) * (int) 3266489909L;
            h ^= h >>> 16;
            return (h & 0xffffffffL) / 4294967296.0;
        }
    }

    private static Geo build(String seed) {
        Rng r = new Rng(seed);
        int rows = 48, segs = 72;
        double height = 1.2;
        double a = 0.15 + r.next() * 0.2;
        double b = 1.5 + r.next() * 2.5;
        double neck = 0.08 + r.next() * 0.08;
        double[][] profile = new double[rows + 1][];
        for (int i = 0; i <= rows; i++) {
            double t = (double) i / rows;
            double y = t * height;
            double rad = 0.18 + a * Math.sin(Math.PI * Math.pow(t, 0.8)) + 0.05 * Math.sin(t * b * Math.PI);
            if (t > 0.82) {
                rad = neck + (rad - neck) * (1 - (t - 0.82) / 0.18) + 0.03 * (t - 0.82) / 0.18;
            }
            profile[i] = new double[]{Math.max(0.03, rad), y};
        }
        int vcount = (rows + 1) * (segs + 1) + 1;
        float[] pos = new float[vcount * 3];
        float[] nrm = new float[vcount * 3];
        int k = 0;
        for (int i = 0; i <= rows; i++) {
            double rad = profile[i][0], y = profile[i][1];
            double[] p0 = profile[Math.max(0, i - 1)], p1 = profile[Math.min(rows, i + 1)];
            double dr = p1[0] - p0[0];
            double dy = p1[1] - p0[1];
            if (dy == 0) dy = 1e-6;
            for (int j = 0; j <= segs; j++) {
                double th = (double) j / segs * Math.PI * 2, c = Math.cos(th), s = Math.sin(th);
                pos[k] = (float) (rad * c); pos[k + 1] = (float) y; pos[k + 2] = (float) (rad * s);
                double nx = dy * c, ny = -dr, nz = dy * s;
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l == 0) l = 1;
                nrm[k] = (float) (nx / l); nrm[k + 1] = (float) (ny / l); nrm[k + 2] = (float) (nz / l);
                k += 3;
            }
        }
        int center = k / 3;
        pos[k] = 0; pos[k + 1] = 0; pos[k + 2] = 0;
        nrm[k] = 0; nrm[k + 1] = -1; nrm[k + 2] = 0;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < segs; j++) {
                int p = i * (segs + 1) + j, q = p + segs + 1;
                idx.add(p); idx.add(q); idx.add(p + 1);
                idx.add(p + 1); idx.add(q); idx.add(q + 1);
            }
        }
        for (int j = 0; j < segs; j++) { idx.add(center); idx.add(j); idx.add(j + 1); }
        double[] color = {0.55 + r.next() * 0.4, 0.35 + r.next() * 0.4, 0.3 + r.next() * 0.5, 1};
        return new Geo(pos, nrm, idx.stream().mapToInt(Integer::intValue).toArray(), color);
    }

    private static int pad4(int n) {
        return (n + 3) & ~3;
    }

    public static byte[] makeGlb(String seed) {
        Geo g = build(seed);
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < g.pos.length; i += 3) {
            for (int c = 0; c < 3; c++) {
                min[c] = Math.min(min[c], g.pos[i + c]);
                max[c] = Math.max(max[c], g.pos[i + c]);
            }
        }
        int pb = g.pos.length * 4, nb = g.nrm.length * 4, ib = g.idx.length * 4;
        ByteBuffer bin = ByteBuffer.allocate(pad4(pb + nb + ib)).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : g.pos) bin.putFloat(f);
        for (float f : g.nrm) bin.putFloat(f);
        for (int i : g.idx) bin.putInt(i);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("asset", Map.of("version", "2.0", "generator", "3DWorld AI Studio procedural"));
        json.put("scene", 0);
        json.put("scenes", List.of(Map.of("nodes", List.of(0))));
        json.put("nodes", List.of(Map.of("mesh", 0, "name", "ProceduralModel")));
        json.put("meshes", List.of(Map.of("primitives", List.of(Map.of("attributes", Map.of("POSITION", 0, "NORMAL", 1), "indices", 2, "material", 0)))));
        json.put("materials", List.of(Map.of("name", "DefaultMat", "pbrMetallicRoughness",
                Map.of("baseColorFactor", g.color, "metallicFactor", 0.05, "roughnessFactor", 0.6))));
        json.put("buffers", List.of(Map.of("byteLength", pb + nb + ib)));
        json.put("bufferViews", List.of(
                Map.of("buffer", 0, "byteOffset", 0, "byteLength", pb, "target", 34962),
                Map.of("buffer", 0, "byteOffset", pb, "byteLength", nb, "target", 34962),
                Map.of("buffer", 0, "byteOffset", pb + nb, "byteLength", ib, "target", 34963)));
        json.put("accessors", List.of(
                Map.of("bufferView", 0, "componentType", 5126, "count", g.pos.length / 3, "type", "VEC3", "min", min, "max", max),
                Map.of("bufferView", 1, "componentType", 5126, "count", g.nrm.length / 3, "type", "VEC3"),
                Map.of("bufferView", 2, "componentType", 5125, "count", g.idx.length, "type", "SCALAR")));

        byte[] js;
        try {
            js = MAPPER.writeValueAsBytes(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize GLTF json", e);
        }
        int jsLen = pad4(js.length);
        int binLen = bin.capacity();
        ByteBuffer out = ByteBuffer.allocate(12 + 8 + jsLen + 8 + binLen).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546c67).putInt(2).putInt(12 + 8 + jsLen + 8 + binLen);
        out.putInt(jsLen).putInt(0x4e4f534a).put(js);
        for (int i = js.length; i < jsLen; i++) out.put((byte) 0x20);
        out.putInt(binLen).putInt(0x004e4942).put(bin.array());
        return out.array();
    }

    public static byte[] makeStl(String seed) {
        Geo g = build(seed);
        int tris = g.idx.length / 3;
        ByteBuffer buf = ByteBuffer.allocate(84 + tris * 50).order(ByteOrder.LITTLE_ENDIAN);
        byte[] header = "3DWorld AI Studio mock".getBytes(StandardCharsets.US_ASCII);
        buf.put(header);
        buf.position(80);
        buf.putInt(tris);
        for (int t = 0; t < tris; t++) {
            float[][] v = new float[3][3];
            for (int c = 0; c < 3; c++) {
                int i = g.idx[t * 3 + c] * 3;
                v[c] = new float[]{g.pos[i], g.pos[i + 1], g.pos[i + 2]};
            }
            double ux = v[1][0] - v[0][0], uy = v[1][1] - v[0][1], uz = v[1][2] - v[0][2];
            double wx = v[2][0] - v[0][0], wy = v[2][1] - v[0][1], wz = v[2][2] - v[0][2];
            double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l == 0) l = 1;
            buf.putFloat((float) (nx / l)).putFloat((float) (ny / l)).putFloat((float) (nz / l));
            for (float[] p : v) for (float x : p) buf.putFloat(x * 100);
            buf.putShort((short) 0);
        }
        return buf.array();
    }
public static byte[] makeObj(String seed) {
        Geo g = build(seed);
        StringBuilder sb = new StringBuilder();
        sb.append("# 3DWorld AI Studio mock OBJ\n");
        sb.append("o ProceduralModel\n");
        for (int i = 0; i < g.pos.length; i += 3) {
            sb.append(String.format(java.util.Locale.ROOT, "v %.6f %.6f %.6f\n", g.pos[i], g.pos[i + 1], g.pos[i + 2]));
        }
        for (int i = 0; i < g.nrm.length; i += 3) {
            sb.append(String.format(java.util.Locale.ROOT, "vn %.6f %.6f %.6f\n", g.nrm[i], g.nrm[i + 1], g.nrm[i + 2]));
        }
        sb.append("s 1\n");
        int tris = g.idx.length / 3;
        for (int t = 0; t < tris; t++) {
            int v1 = g.idx[t * 3] + 1;
            int v2 = g.idx[t * 3 + 1] + 1;
            int v3 = g.idx[t * 3 + 2] + 1;
            sb.append("f ").append(v1).append("//").append(v1).append(" ")
              .append(v2).append("//").append(v2).append(" ")
              .append(v3).append("//").append(v3).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] makeFbx(String seed) {
        byte[] prefix = "Kaydara FBX Binary  \0\u001a\0".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(prefix);
        buf.putInt(7400);
        for (int i = 0; i < 13; i++) buf.put((byte) 0);
        while (buf.hasRemaining()) buf.put((byte) 0);
        return buf.array();
    }
}
