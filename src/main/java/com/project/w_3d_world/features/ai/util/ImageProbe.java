package com.project.w_3d_world.features.ai.util;

import java.nio.charset.StandardCharsets;

/**
 * Inspects real MIME types via magic bytes and reads JPEG / PNG / WEBP image dimensions
 * without any external image processing dependencies.
 */
public final class ImageProbe {
    private ImageProbe() {}

    public record Dim(int width, int height) {}

    public static String sniffMime(byte[] b) {
        if (b == null || b.length < 4) return null;
        if ((b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static int u8(byte[] b, int i) { return b[i] & 0xff; }
    private static int be16(byte[] b, int i) { return (u8(b, i) << 8) | u8(b, i + 1); }
    private static int le16(byte[] b, int i) { return u8(b, i) | (u8(b, i + 1) << 8); }
    private static int le24(byte[] b, int i) { return u8(b, i) | (u8(b, i + 1) << 8) | (u8(b, i + 2) << 16); }
    private static int be32(byte[] b, int i) { return (be16(b, i) << 16) | be16(b, i + 2); }

    public static Dim dimensions(byte[] b, String mime) {
        if (b == null || mime == null) throw new IllegalArgumentException("unreadable image");
        switch (mime) {
            case "image/png" -> {
                if (b.length < 24) break;
                return new Dim(be32(b, 16), be32(b, 20));
            }
            case "image/jpeg" -> {
                int i = 2;
                while (i + 9 < b.length) {
                    if (u8(b, i) != 0xff) { i++; continue; }
                    int marker = u8(b, i + 1);
                    if (marker == 0xff) { i++; continue; }
                    if (marker == 0xd8 || marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) { i += 2; continue; }
                    int len = be16(b, i + 2);
                    boolean sof = marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
                    if (sof) return new Dim(be16(b, i + 7), be16(b, i + 5));
                    i += 2 + len;
                }
            }
            case "image/webp" -> {
                if (b.length < 30) break;
                String chunk = new String(b, 12, 4, StandardCharsets.US_ASCII);
                switch (chunk) {
                    case "VP8 " -> { return new Dim(le16(b, 26) & 0x3fff, le16(b, 28) & 0x3fff); }
                    case "VP8L" -> {
                        int bits = u8(b, 21) | (u8(b, 22) << 8) | (u8(b, 23) << 16) | (u8(b, 24) << 24);
                        return new Dim((bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1);
                    }
                    case "VP8X" -> { return new Dim(le24(b, 24) + 1, le24(b, 27) + 1); }
                    default -> { }
                }
            }
            default -> { }
        }
        throw new IllegalArgumentException("unreadable image");
    }
}
