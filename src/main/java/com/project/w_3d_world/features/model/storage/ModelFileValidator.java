package com.project.w_3d_world.features.model.storage;

import com.project.w_3d_world.features.model.enums.ModelFileRole;

import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

import static com.project.w_3d_world.features.model.storage.ModelStorageException.Code.*;

/**
 * Bounded structural inspection, not a mesh/printability validator or a malware scanner.
 * Limits use decimal MB (1 MB = 1,000,000 bytes). No textures or external references are fetched.
 * 3MF inspection additionally caps entries, expanded bytes and expansion ratio.
 */
public final class ModelFileValidator {
    public static final long MODEL_LIMIT = 50_000_000;
    public static final long SCENE_LIMIT = 5_000_000;
    public static final long PREVIEW_LIMIT = 10_000_000;
    private static final String MODEL_NS = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02";
    private static final String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String TYPE_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String MODEL_REL = "http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel";
    private static final String MODEL_MIME = "application/vnd.ms-package.3dmanufacturing-3dmodel+xml";
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(100)
                    .maxStringLength(1_000_000).maxNumberLength(128).build()).build();

    public record Format(String extension, String mimeType, long maxBytes) {}

    public Format validateMetadata(String filename, String declaredMime, ModelFileRole role) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.chars().anyMatch(c -> c < 32 || c == 127)
                || filename.contains("/") || filename.contains("\\") || filename.contains(":")) {
            throw new ModelStorageException(INVALID_METADATA, "A plain filename with a supported extension is required.");
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1 || role == null) {
            throw new ModelStorageException(INVALID_METADATA, "Filename extension and file role are required.");
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        Format format = switch (ext) {
            case "glb" -> new Format(ext, "model/gltf-binary", MODEL_LIMIT);
            case "stl" -> new Format(ext, "model/stl", MODEL_LIMIT);
            case "obj" -> new Format(ext, "model/obj", MODEL_LIMIT);
            case "3mf" -> new Format(ext, "model/3mf", MODEL_LIMIT);
            case "json" -> new Format(ext, "application/json", SCENE_LIMIT);
            case "png" -> new Format(ext, "image/png", PREVIEW_LIMIT);
            case "jpg", "jpeg" -> new Format(ext, "image/jpeg", PREVIEW_LIMIT);
            default -> throw new ModelStorageException(INVALID_METADATA, "Unsupported file extension.");
        };
        boolean compatible = switch (role) {
            case EDITABLE_SCENE -> ext.equals("json");
            case PREVIEW -> Set.of("png", "jpg", "jpeg").contains(ext);
            case SOURCE_MODEL, VIEWABLE_MODEL, PRINT_MODEL -> Set.of("glb", "stl", "obj", "3mf").contains(ext);
        };
        if (!compatible) {
            throw new ModelStorageException(INVALID_METADATA, "File role is incompatible with the format.");
        }
        if (declaredMime != null && !declaredMime.isBlank()) {
            String mime = declaredMime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            Set<String> aliases = switch (ext) {
                case "stl" -> Set.of("application/sla", "application/vnd.ms-pki.stl", "application/octet-stream", "text/plain");
                case "obj" -> Set.of("text/plain", "application/octet-stream");
                case "3mf" -> Set.of("application/vnd.ms-package.3dmanufacturing-3dmodel+xml", "application/zip", "application/octet-stream");
                case "glb" -> Set.of("application/octet-stream");
                default -> Set.of();
            };
            if (!mime.equals(format.mimeType()) && !aliases.contains(mime)) {
                throw new ModelStorageException(INVALID_METADATA, "Declared content type is incompatible with the format.");
            }
        }
        return format;
    }

    public void validateContent(Path file, Format format) throws IOException {
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(format.maxBytes() + 1));
            require(bytes.length > 0);
            if (bytes.length > format.maxBytes()) {
                throw new ModelStorageException(TOO_LARGE, "File exceeds its category size limit.");
            }
            switch (format.extension()) {
                case "glb" -> glb(bytes);
                case "stl" -> stl(bytes);
                case "obj" -> obj(bytes);
                case "json" -> json(bytes);
                case "3mf" -> threeMf(file, bytes);
                case "png", "jpg", "jpeg" -> image(bytes, format.extension());
                default -> throw invalid();
            }
        } catch (ModelStorageException exception) {
            throw exception;
        } catch (CharacterCodingException | XMLStreamException | java.util.zip.ZipException
                | javax.imageio.IIOException exception) {
            throw new ModelStorageException(INVALID_CONTENT, "File content is malformed or unsupported.", exception);
        } catch (IllegalArgumentException | tools.jackson.core.JacksonException exception) {
            throw new ModelStorageException(INVALID_CONTENT, "File content is malformed or unsupported.", exception);
        }
    }

    private static void json(byte[] bytes) throws CharacterCodingException {
        try (var parser = JSON.createParser(ObjectReadContext.empty(), utf8(bytes))) {
            require(parser.nextToken() == JsonToken.START_OBJECT);
            // Consume every token (including long strings) so constraints are actually enforced.
            int depth = 1;
            while (depth > 0) {
                JsonToken token = parser.nextToken();
                require(token != null);
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) depth++;
                if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) depth--;
                if (token == JsonToken.VALUE_STRING) parser.getString();
            }
            require(parser.nextToken() == null);
        }
    }

    private static void glb(byte[] bytes) throws CharacterCodingException {
        require(bytes.length >= 24);
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(data.getInt() == 0x46546c67 && data.getInt() == 2);
        require(Integer.toUnsignedLong(data.getInt()) == bytes.length);
        int chunks = 0;
        while (data.hasRemaining()) {
            require(data.remaining() >= 8);
            long length = Integer.toUnsignedLong(data.getInt());
            int type = data.getInt();
            require(length > 0 && length % 4 == 0 && length <= data.remaining());
            if (chunks == 0) {
                require(type == 0x4e4f534a && length <= SCENE_LIMIT);
                byte[] content = new byte[(int) length];
                data.get(content);
                json(content);
                gltfAsset(content);
            } else {
                require(chunks == 1 && type == 0x004e4942);
                data.position(data.position() + (int) length);
            }
            chunks++;
        }
        require(chunks > 0);
    }

    private static void gltfAsset(byte[] bytes) throws CharacterCodingException {
        boolean version = false;
        try (var parser = JSON.createParser(ObjectReadContext.empty(), utf8(bytes))) {
            parser.nextToken(); // The complete JSON structure was checked by json().
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.getString();
                JsonToken value = parser.nextToken();
                if (name.equals("asset")) {
                    require(value == JsonToken.START_OBJECT);
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String property = parser.getString();
                        JsonToken assetValue = parser.nextToken();
                        if (property.equals("version")) {
                            require(assetValue == JsonToken.VALUE_STRING && parser.getString().equals("2.0"));
                            version = true;
                        }
                        parser.skipChildren();
                    }
                } else parser.skipChildren();
            }
        }
        require(version);
    }

    private static void stl(byte[] bytes) throws IOException {
        if (bytes.length >= 84) {
            ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            long count = Integer.toUnsignedLong(data.getInt(80));
            if (count > 0 && 84 + count * 50 == bytes.length) {
                data.position(84);
                for (long i = 0; i < count; i++) {
                    for (int j = 0; j < 12; j++) require(Float.isFinite(data.getFloat()));
                    data.getShort();
                }
                return;
            }
        }
        try (var reader = new BufferedReader(new StringReader(utf8(bytes)))) {
            String line = nextLine(reader);
            require(line != null && (line.equals("solid") || line.startsWith("solid ")));
            int facets = 0;
            while ((line = nextLine(reader)) != null && !line.startsWith("endsolid")) {
                vector(line, "facet normal", 3);
                require("outer loop".equals(nextLine(reader)));
                for (int i = 0; i < 3; i++) vector(nextLine(reader), "vertex", 3);
                require("endloop".equals(nextLine(reader)));
                require("endfacet".equals(nextLine(reader)));
                facets++;
            }
            require(facets > 0 && line != null && (line.equals("endsolid") || line.startsWith("endsolid ")));
            require(nextLine(reader) == null);
        }
    }

    private static void obj(byte[] bytes) throws IOException {
        long vertices = 0, textures = 0, normals = 0, faces = 0;
        try (var reader = new BufferedReader(new StringReader(utf8(bytes)))) {
            String line;
            while ((line = nextLine(reader)) != null) {
                line = line.split("#", 2)[0].trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> {
                        require(parts.length == 4 || parts.length == 5 || parts.length == 7);
                        numbers(parts, 1);
                        vertices++;
                    }
                    case "vt" -> { require(parts.length >= 2 && parts.length <= 4); numbers(parts, 1); textures++; }
                    case "vn" -> { require(parts.length == 4); numbers(parts, 1); normals++; }
                    case "f" -> {
                        require(parts.length >= 4);
                        for (int i = 1; i < parts.length; i++) {
                            String[] indices = parts[i].split("/", -1);
                            require(indices.length <= 3);
                            index(indices[0], vertices);
                            if (indices.length > 1 && !indices[1].isEmpty()) index(indices[1], textures);
                            if (indices.length > 2) index(indices[2], normals);
                            if (indices.length == 2) require(!indices[1].isEmpty());
                        }
                        faces++;
                    }
                    // Names/references are retained as data; never opened or fetched here.
                    case "o", "g", "s", "mtllib", "usemtl" -> require(parts.length >= 2);
                    default -> throw invalid();
                }
            }
        }
        require(vertices >= 3 && faces > 0);
    }

    private static void index(String value, long count) {
        long index = Long.parseLong(value);
        require(index != 0 && index >= -count && index <= count);
    }

    private static void vector(String line, String prefix, int count) {
        require(line != null && line.startsWith(prefix + " "));
        String[] values = line.substring(prefix.length()).trim().split("\\s+");
        require(values.length == count);
        numbers(values, 0);
    }

    private static void numbers(String[] values, int start) {
        for (int i = start; i < values.length; i++) require(Double.isFinite(Double.parseDouble(values[i])));
    }

    private static String nextLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            require(line.length() <= 65_536);
            if (!line.isBlank()) return line.trim().replaceAll("\\s+", " ");
        }
        return null;
    }

    private static void threeMf(Path file, byte[] bytes) throws IOException, XMLStreamException {
        require(bytes.length >= 22 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4);
        // Require a complete central directory/end record; no extracted paths or nested archives.
        int eocd = -1;
        for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65_557); i--) {
            if (bytes[i] == 'P' && bytes[i + 1] == 'K' && bytes[i + 2] == 5 && bytes[i + 3] == 6) {
                int comment = (bytes[i + 20] & 255) | ((bytes[i + 21] & 255) << 8);
                if (i + 22 + comment == bytes.length) { eocd = i; break; }
            }
        }
        require(eocd >= 0);
        Map<String, byte[]> xml = new HashMap<>();
        Set<String> names = new HashSet<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                require(names.size() < 256 && safeEntry(name) && names.add(name));
                if (entry.isDirectory()) continue;
                require(entry.getSize() >= 0 && entry.getSize() <= 16_000_000);
                require(entry.getSize() <= Math.max(1, entry.getCompressedSize()) * 100);
                CRC32 crc = new CRC32();
                long size = 0;
                boolean inspect = name.equals("[Content_Types].xml") || name.equals("_rels/.rels")
                        || (name.startsWith("3D/") && name.endsWith(".model"));
                var content = inspect ? new ByteArrayOutputStream() : null;
                try (var input = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        size += read;
                        total += read;
                        require(size <= 16_000_000 && total <= MODEL_LIMIT);
                        if (inspect) { require(size <= SCENE_LIMIT); content.write(buffer, 0, read); }
                        crc.update(buffer, 0, read);
                    }
                }
                require(size == entry.getSize() && crc.getValue() == entry.getCrc());
                if (inspect) xml.put(name, content.toByteArray());
            }
        }
        require(xml.containsKey("[Content_Types].xml") && xml.containsKey("_rels/.rels"));
        String target = inspectXml(xml.get("_rels/.rels"), "Relationships", REL_NS, null);
        require(target != null && target.startsWith("3D/") && target.endsWith(".model") && xml.containsKey(target));
        require(inspectXml(xml.get("[Content_Types].xml"), "Types", TYPE_NS, target) != null);
        inspectXml(xml.get(target), "model", MODEL_NS, null);
    }

    private static boolean safeEntry(String name) {
        return !name.isEmpty() && !name.startsWith("/") && !name.contains("\\") && !name.contains(":")
                && name.chars().noneMatch(c -> c < 32 || c == 127)
                && Arrays.stream(name.split("/", -1)).noneMatch(s -> s.equals("..") || s.equals("."));
    }

    private static String inspectXml(byte[] bytes, String root, String namespace, String modelTarget) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, base, ns) -> { throw new XMLStreamException("External entities forbidden"); });
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
        String result = null;
        int depth = 0, events = 0;
        boolean seen = false, resources = false, build = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                require(++events <= 500_000 && event != XMLStreamConstants.DTD && event != XMLStreamConstants.ENTITY_REFERENCE);
                if (event == XMLStreamConstants.START_ELEMENT) {
                    require(++depth <= 100);
                    if (!seen) {
                        require(root.equals(reader.getLocalName()) && namespace.equals(reader.getNamespaceURI()));
                        seen = true;
                    }
                    if (depth == 2 && namespace.equals(reader.getNamespaceURI())) {
                        String name = reader.getLocalName();
                        if (root.equals("Relationships") && name.equals("Relationship")
                                && MODEL_REL.equals(reader.getAttributeValue(null, "Type"))) {
                            require(!"External".equalsIgnoreCase(reader.getAttributeValue(null, "TargetMode")));
                            require(result == null);
                            result = reader.getAttributeValue(null, "Target");
                            require(result != null);
                            if (result.startsWith("/")) result = result.substring(1);
                            require(safeEntry(result));
                        }
                        if (root.equals("Types") && name.equals("Default")
                                && "model".equals(reader.getAttributeValue(null, "Extension"))
                                && MODEL_MIME.equals(reader.getAttributeValue(null, "ContentType"))) result = "model";
                        if (root.equals("Types") && name.equals("Override")
                                && ("/" + modelTarget).equals(reader.getAttributeValue(null, "PartName"))
                                && MODEL_MIME.equals(reader.getAttributeValue(null, "ContentType"))) result = "model";
                        if (root.equals("model")) {
                            resources |= name.equals("resources");
                            build |= name.equals("build");
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) depth--;
            }
            require(seen && depth == 0 && (!root.equals("model") || (resources && build)));
        } finally { reader.close(); }
        return result;
    }

    private static void image(byte[] bytes, String ext) throws IOException {
        if (ext.equals("png")) png(bytes);
        else require(bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[bytes.length - 2] & 255) == 255 && (bytes[bytes.length - 1] & 255) == 217);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            require(readers.hasNext());
            var reader = readers.next();
            try {
                require(reader.getFormatName().equalsIgnoreCase(ext.equals("png") ? "png" : "jpeg"));
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                require(width > 0 && height > 0 && (long) width * height <= 16_000_000);
                boolean[] warning = {false};
                reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                require(reader.read(0) != null && !warning[0]);
            } finally { reader.dispose(); }
        }
    }

    private static void png(byte[] bytes) {
        require(bytes.length >= 45 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}));
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        data.position(8);
        boolean header = false, pixels = false, end = false;
        while (data.hasRemaining()) {
            require(!end && data.remaining() >= 12);
            int size = data.getInt(), start = data.position();
            require(size >= 0 && size <= data.remaining() - 8);
            int type = data.getInt();
            if (!header) { require(type == 0x49484452 && size == 13); header = true; }
            else require(type != 0x49484452);
            pixels |= type == 0x49444154;
            if (type == 0x49454e44) { require(size == 0); end = true; }
            CRC32 crc = new CRC32();
            crc.update(bytes, start, size + 4);
            data.position(start + 4 + size);
            require(crc.getValue() == Integer.toUnsignedLong(data.getInt()));
        }
        require(header && pixels && end);
    }

    private static String utf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void require(boolean valid) {
        if (!valid) throw invalid();
    }

    private static ModelStorageException invalid() {
        return new ModelStorageException(INVALID_CONTENT, "File content is malformed or unsupported.");
    }
}
