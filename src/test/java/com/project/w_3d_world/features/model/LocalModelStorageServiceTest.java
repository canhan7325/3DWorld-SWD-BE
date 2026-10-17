package com.project.w_3d_world.features.model;

import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.storage.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.project.w_3d_world.features.model.enums.ModelFileRole.*;
import static com.project.w_3d_world.features.model.storage.ModelStorageException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalModelStorageServiceTest {
    @TempDir Path temporaryDirectory;
    private Path root;
    private LocalModelStorageService storage;

    @BeforeEach
    void setUp() {
        root = temporaryDirectory.resolve("private-storage");
        storage = new LocalModelStorageService(root);
    }

    @Test
    void st01_validGlbStoredWithVerifiedMetadata() throws Exception {
        byte[] bytes = glb();
        var result = store(bytes, "shape.GLB", "application/octet-stream", SOURCE_MODEL);
        assertEquals("glb", result.fileFormat());
        assertEquals("model/gltf-binary", result.mimeType());
        assertEquals(bytes.length, result.fileSize());
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve(result.storageKey())));
        assertTrue(result.storageKey().matches("models/101/versions/1/files/[0-9a-f-]{36}\\.glb"));
        assertFalse(result.storageKey().contains(temporaryDirectory.toString()));
    }

    @Test
    void st02_binaryAndAsciiStlStored() {
        assertEquals("stl", store(binaryStl(), "binary.stl", "model/stl", PRINT_MODEL).fileFormat());
        assertEquals("model/stl", store(asciiStl(), "ascii.stl", "text/plain", PRINT_MODEL).mimeType());
    }

    @Test
    void st03_modelLimitEnforcedDuringStreaming() throws Exception {
        rejectOversize(ModelFileValidator.MODEL_LIMIT, "large.glb", SOURCE_MODEL);
    }

    @Test
    void st04_emptyFileRejected() throws Exception {
        assertCode(EMPTY_FILE, () -> store(new byte[0], "empty.obj", null, SOURCE_MODEL));
        assertNoFiles();
    }

    @Test
    void st05_unsupportedExtensionRejectedBeforeReading() {
        assertCode(INVALID_METADATA, () -> store(glb(), "model.exe", null, SOURCE_MODEL));
    }

    @Test
    void st06_fakeContentRejectedAndCleaned() throws Exception {
        assertCode(INVALID_CONTENT, () -> store("not a model".getBytes(StandardCharsets.UTF_8), "fake.glb", null, SOURCE_MODEL));
        assertNoFiles();
    }

    @Test
    void st07_traversalAbsoluteAndMalformedKeysRejected() {
        for (String key : List.of("../../secret.txt", "C:\\Windows\\System32\\example.dll",
                "models/12/versions/1/files/../../../secret.txt", "/etc/passwd",
                "models\\1\\versions\\1\\files\\a.glb",
                "models/0/versions/1/files/550e8400-e29b-41d4-a716-446655440000.glb",
                "models/2147483648/versions/1/files/550e8400-e29b-41d4-a716-446655440000.glb")) {
            assertCode(INVALID_KEY, () -> storage.open(key));
            assertCode(INVALID_KEY, () -> storage.deleteForCleanup(key));
        }
    }

    @Test
    void st08_symlinkEscapeRejectedForStoreOpenAndCleanup() throws Exception {
        Files.createDirectories(root);
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        symlink(root.resolve("models"), outside);
        try {
            assertCode(UNSAFE_PATH, () -> store(glb(), "safe.glb", null, SOURCE_MODEL));
            String key = validKey();
            assertCode(UNSAFE_PATH, () -> storage.open(key));
            assertCode(UNSAFE_PATH, () -> storage.deleteForCleanup(key));
            try (var entries = Files.list(outside)) { assertEquals(0, entries.count()); }
        } finally { Files.delete(root.resolve("models")); }
    }

    @Test
    void st09_sameOriginalFilenameProducesDifferentKeys() {
        var one = store(glb(), "same.glb", null, SOURCE_MODEL);
        var two = store(glb(), "same.glb", null, SOURCE_MODEL);
        assertNotEquals(one.storageKey(), two.storageKey());
    }

    @Test
    void st10_interruptedInputCleansPartialFileAndPreservesCause() throws Exception {
        IOException failure = new IOException("injected stream failure");
        InputStream input = new InputStream() {
            boolean sent;
            @Override public int read() throws IOException { throw failure; }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (sent) throw failure;
                sent = true;
                Arrays.fill(bytes, offset, offset + Math.min(32, length), (byte) 1);
                return Math.min(32, length);
            }
        };
        var exception = assertThrows(ModelStorageException.class,
                () -> storage.store(input, "partial.glb", null, SOURCE_MODEL, 101, 1));
        assertEquals(IO_FAILURE, exception.getCode());
        assertSame(failure, exception.getCause());
        assertNoFiles();
    }

    @Test
    void st11_openReturnsOriginalBytes() throws Exception {
        var result = store(glb(), "shape.glb", null, VIEWABLE_MODEL);
        try (var input = storage.open(result.storageKey())) { assertArrayEquals(glb(), input.readAllBytes()); }
    }

    @Test
    void st12_cleanupDeletesOnlyIntendedFileAndIsIdempotent() throws Exception {
        var one = store(glb(), "shape.glb", null, SOURCE_MODEL);
        var two = store(glb(), "shape.glb", null, SOURCE_MODEL);
        assertTrue(storage.deleteForCleanup(one.storageKey()));
        assertFalse(storage.deleteForCleanup(one.storageKey()));
        try (var input = storage.open(two.storageKey())) { assertArrayEquals(glb(), input.readAllBytes()); }
        assertTrue(Files.isDirectory(root.resolve("models/101/versions/1/files")));
    }

    @Test
    void st13_invalidCleanupKeyDoesNotDeleteOutsideFile() throws Exception {
        Path outside = Files.writeString(temporaryDirectory.resolve("keep.txt"), "keep");
        assertCode(INVALID_KEY, () -> storage.deleteForCleanup(outside.toString()));
        assertCode(INVALID_KEY, () -> storage.deleteForCleanup("../keep.txt"));
        assertEquals("keep", Files.readString(outside));
    }

    @Test
    void st14_missingConfigurationFailsOnlyWhenUsed() {
        var service = assertDoesNotThrow(() -> new LocalModelStorageService(new MockEnvironment()));
        var exception = assertThrows(ModelStorageException.class,
                () -> service.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, 1, 1));
        assertEquals(CONFIGURATION, exception.getCode());
        assertTrue(exception.getMessage().contains("MODEL_STORAGE_ROOT"));
    }

    @Test
    void st15_sha256MatchesStoredBytes() throws Exception {
        var result = store(glb(), "a.glb", null, SOURCE_MODEL);
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(glb()));
        assertEquals(expected, result.fileHash());
    }

    @Test
    void st16_sceneSizeLimitEnforced() throws Exception {
        rejectOversize(ModelFileValidator.SCENE_LIMIT, "scene.json", EDITABLE_SCENE);
    }

    @Test
    void st17_previewSizeLimitEnforced() throws Exception {
        rejectOversize(ModelFileValidator.PREVIEW_LIMIT, "preview.png", PREVIEW);
    }

    @Test
    void st18_duplicateDestinationNeverOverwrites() throws Exception {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var fixed = new LocalModelStorageService(root, new ModelStorageKeyFactory(() -> uuid));
        var first = fixed.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, 101, 1);
        byte[] before = Files.readAllBytes(root.resolve(first.storageKey()));
        assertCode(COLLISION, () -> fixed.store(new ByteArrayInputStream(glb()), "b.glb", null, SOURCE_MODEL, 101, 1));
        assertArrayEquals(before, Files.readAllBytes(root.resolve(first.storageKey())));
        try (var files = Files.walk(root)) { assertEquals(1, files.filter(Files::isRegularFile).count()); }
    }

    @Test
    void st19_missingFileHasControlledBehavior() {
        assertCode(NOT_FOUND, () -> storage.open(validKey()));
        assertFalse(storage.deleteForCleanup(validKey()));
    }

    @Test
    void st20_unsafeRootAndParentRejected() throws Exception {
        Files.writeString(root, "not a directory");
        assertCode(UNSAFE_PATH, () -> store(glb(), "a.glb", null, SOURCE_MODEL));
        var relative = new LocalModelStorageService(Path.of("relative-storage"));
        assertCode(CONFIGURATION, () -> relative.open(validKey()));
        Path parent = Files.writeString(temporaryDirectory.resolve("parent-file"), "keep");
        var unsafe = new LocalModelStorageService(parent.resolve("storage"));
        assertCode(UNSAFE_PATH, () -> unsafe.open(validKey()));
        assertEquals("keep", Files.readString(parent));
    }

    @Test
    void objGeometryAcceptedButTextAndBadIndicesRejected() {
        byte[] obj = text("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
        assertEquals("model/obj", store(obj, "mesh.obj", "text/plain", SOURCE_MODEL).mimeType());
        assertCode(INVALID_CONTENT, () -> store(text("hello world"), "bad.obj", null, SOURCE_MODEL));
        assertCode(INVALID_CONTENT, () -> store(text("v 0 0 0\nf 1 2 3"), "bad.obj", null, SOURCE_MODEL));
    }

    @Test
    void jsonStrictUtf8SyntaxAndDepthChecked() {
        assertEquals("json", store(text("{\"objects\":[]}"), "scene.json", "application/json; charset=utf-8", EDITABLE_SCENE).fileFormat());
        for (byte[] invalid : List.of(text("{bad}"), text("{} {}"), text("{\"a\":"),
                new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'},
                text("{\"a\":".repeat(101) + "0" + "}".repeat(101)))) {
            assertCode(INVALID_CONTENT, () -> store(invalid, "bad.json", null, EDITABLE_SCENE));
        }
    }

    @Test
    void pngAndJpegDecodedWithCanonicalMime() throws Exception {
        for (String ext : List.of("png", "jpg", "jpeg")) {
            byte[] bytes = image(ext.equals("png") ? "png" : "jpeg");
            var result = store(bytes, "preview." + ext, null, PREVIEW);
            assertEquals(ext.equals("png") ? "image/png" : "image/jpeg", result.mimeType());
            try (var input = storage.open(result.storageKey())) { assertArrayEquals(bytes, input.readAllBytes()); }
        }
    }

    @Test
    void truncatedImagesAndPngCrcMismatchRejected() throws Exception {
        byte[] png = image("png");
        byte[] jpg = image("jpeg");
        assertCode(INVALID_CONTENT, () -> store(Arrays.copyOf(png, png.length - 4), "bad.png", null, PREVIEW));
        assertCode(INVALID_CONTENT, () -> store(Arrays.copyOf(jpg, jpg.length - 2), "bad.jpg", null, PREVIEW));
        png[png.length - 1] ^= 1;
        assertCode(INVALID_CONTENT, () -> store(png, "bad.png", null, PREVIEW));
    }

    @Test
    void validThreeMfPackageAccepted() throws Exception {
        var result = store(zip(threeMfEntries()), "mesh.3mf", "application/zip", PRINT_MODEL);
        assertEquals("model/3mf", result.mimeType());
    }

    @Test
    void threeMfMissingEntriesTraversalAndBombRejected() throws Exception {
        assertCode(INVALID_CONTENT, () -> store(zip(Map.of("readme.txt", text("not 3mf"))), "bad.3mf", null, PRINT_MODEL));
        var entries = threeMfEntries();
        entries.put("../escape.txt", text("bad"));
        assertCode(INVALID_CONTENT, () -> store(zip(entries), "bad.3mf", null, PRINT_MODEL));
        entries.remove("../escape.txt");
        entries.put("large.bin", new byte[1_000_000]);
        assertCode(INVALID_CONTENT, () -> store(zip(entries), "bomb.3mf", null, PRINT_MODEL));
        assertNoFiles();
    }

    @Test
    void threeMfExternalXmlEntitiesAndExternalModelTargetsRejected() throws Exception {
        var entries = threeMfEntries();
        entries.put("3D/3dmodel.model", text("<!DOCTYPE model [<!ENTITY x SYSTEM 'file:///secret'>]>"
                + "<model xmlns='http://schemas.microsoft.com/3dmanufacturing/core/2015/02'><resources>&x;</resources><build/></model>"));
        assertCode(INVALID_CONTENT, () -> store(zip(entries), "bad.3mf", null, PRINT_MODEL));
        entries.put("_rels/.rels", text("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship TargetMode='External' Target='https://example.invalid/a.model' "
                + "Type='http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel' Id='r1'/></Relationships>"));
        assertCode(INVALID_CONTENT, () -> store(zip(entries), "bad.3mf", null, PRINT_MODEL));
    }

    @Test
    void glbBadVersionLengthAndChunksRejected() {
        byte[] version = glb();
        version[4] = 1;
        byte[] length = glb();
        length[8] = 0;
        byte[] chunk = glb();
        chunk[16] = 0;
        for (byte[] invalid : List.of(version, length, chunk)) {
            assertCode(INVALID_CONTENT, () -> store(invalid, "bad.glb", null, SOURCE_MODEL));
        }
        byte[] noAsset = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46546c67).putInt(2).putInt(24).putInt(4).putInt(0x4e4f534a)
                .put(text("{}  ")).array();
        assertCode(INVALID_CONTENT, () -> store(noAsset, "bad.glb", null, SOURCE_MODEL));
    }

    @Test
    void malformedStlAndNonFiniteCoordinatesRejected() {
        byte[] stl = binaryStl();
        ByteBuffer.wrap(stl).order(ByteOrder.LITTLE_ENDIAN).putFloat(84, Float.NaN);
        assertCode(INVALID_CONTENT, () -> store(stl, "bad.stl", null, PRINT_MODEL));
        assertCode(INVALID_CONTENT, () -> store(text("solid fake\nendsolid fake"), "bad.stl", null, PRINT_MODEL));
    }

    @Test
    void filenameRoleMimeAndIdValidation() {
        for (String name : List.of("../a.glb", "a\\b.glb", "a.glb:stream", "file", ".glb", "x\u0000.glb")) {
            assertCode(INVALID_METADATA, () -> store(glb(), name, null, SOURCE_MODEL));
        }
        assertCode(INVALID_METADATA, () -> store(glb(), "a.glb", "image/png", SOURCE_MODEL));
        assertCode(INVALID_METADATA, () -> store(glb(), "a.glb", null, PREVIEW));
        assertCode(INVALID_METADATA, () -> storage.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, -1, 1));
        assertCode(INVALID_METADATA, () -> storage.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, 1, null));
    }

    @Test
    void atomicMoveUnsupportedFailsClosedAndCleansTemporaryFile() throws Exception {
        var unsupported = new LocalModelStorageService(root) {
            @Override protected void moveAtomically(Path source, Path destination) throws IOException {
                throw new AtomicMoveNotSupportedException("source", "destination", "injected");
            }
        };
        assertCode(ATOMIC_MOVE_UNSUPPORTED, () -> unsupported.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, 101, 1));
        assertNoFiles();
    }

    @Test
    void diskPublicationFailureCleansTemporaryFile() throws Exception {
        var failure = new LocalModelStorageService(root) {
            @Override protected void moveAtomically(Path source, Path destination) throws IOException {
                throw new AccessDeniedException("injected");
            }
        };
        assertCode(IO_FAILURE, () -> failure.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, 101, 1));
        assertNoFiles();
    }

    @Test
    void finalSymlinkCannotBeOpenedOrDeleted() throws Exception {
        Path outside = Files.write(temporaryDirectory.resolve("outside.glb"), glb());
        Path target = root.resolve(validKey());
        Files.createDirectories(target.getParent());
        symlink(target, outside);
        assertCode(UNSAFE_PATH, () -> storage.open(validKey()));
        assertCode(UNSAFE_PATH, () -> storage.deleteForCleanup(validKey()));
        assertArrayEquals(glb(), Files.readAllBytes(outside));
        assertTrue(Files.isSymbolicLink(target));
    }

    @Test
    void rootSymlinkRejected() throws Exception {
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside-root"));
        symlink(root, outside);
        try { assertCode(UNSAFE_PATH, () -> store(glb(), "a.glb", null, SOURCE_MODEL)); }
        finally { Files.delete(root); }
    }

    @Test
    void cleanupFailureReportedWithoutRecursiveDeletion() throws Exception {
        Path[] changed = {null};
        var failure = new LocalModelStorageService(root) {
            @Override protected void moveAtomically(Path source, Path destination) throws IOException {
                Files.delete(source);
                Files.createDirectory(source);
                Files.writeString(source.resolve("keep.txt"), "keep");
                changed[0] = source;
                throw new IOException("injected publication failure and filesystem interference");
            }
        };
        var exception = assertThrows(ModelStorageException.class,
                () -> failure.store(new ByteArrayInputStream(glb()), "a.glb", null, SOURCE_MODEL, 101, 1));
        assertEquals(IO_FAILURE, exception.getCode());
        assertEquals(1, exception.getSuppressed().length);
        assertEquals(CLEANUP_FAILURE, ((ModelStorageException) exception.getSuppressed()[0]).getCode());
        assertEquals("keep", Files.readString(changed[0].resolve("keep.txt")));
        assertFalse(exception.getMessage().contains(root.toString()));
    }

    @Test
    void threadInterruptionCleansPartialFileAndRetainsInterruptFlag() throws Exception {
        try {
            Thread.currentThread().interrupt();
            var exception = assertThrows(ModelStorageException.class,
                    () -> store(glb(), "a.glb", null, SOURCE_MODEL));
            assertEquals(IO_FAILURE, exception.getCode());
            assertInstanceOf(InterruptedIOException.class, exception.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertNoFiles();
    }

    @Test
    void callerRetainsOwnershipOfInputStream() {
        boolean[] closed = {false};
        var input = new ByteArrayInputStream(glb()) {
            @Override public void close() { closed[0] = true; }
        };
        storage.store(input, "a.glb", null, SOURCE_MODEL, 101, 1);
        assertFalse(closed[0]);
    }

    private StoredModelFile store(byte[] bytes, String name, String mime, ModelFileRole role) {
        return storage.store(new ByteArrayInputStream(bytes), name, mime, role, 101, 1);
    }

    private void rejectOversize(long limit, String filename, ModelFileRole role) throws Exception {
        var input = new InputStream() {
            long remaining = limit + 1;
            @Override public int read() { if (remaining-- > 0) return 0; return -1; }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(remaining, length);
                Arrays.fill(bytes, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        };
        assertCode(TOO_LARGE, () -> storage.store(input, filename, null, role, 101, 1));
        assertNoFiles();
    }

    private void assertNoFiles() throws IOException {
        if (!Files.exists(root)) return;
        try (var files = Files.walk(root)) { assertEquals(0, files.filter(Files::isRegularFile).count()); }
    }

    private static void assertCode(ModelStorageException.Code code, org.junit.jupiter.api.function.Executable operation) {
        var error = assertThrows(ModelStorageException.class, operation);
        assertEquals(code, error.getCode(), error::getMessage);
    }

    private static void symlink(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | FileSystemException exception) {
            // Directory junctions exercise Windows reparse-point protection without requiring
            // SeCreateSymbolicLinkPrivilege. Both endpoints are inside this test's @TempDir.
            if (System.getProperty("os.name").startsWith("Windows") && Files.isDirectory(target)) {
                var process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
                        .redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                try {
                    if (process.waitFor() == 0) return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Test junction creation interrupted", interrupted);
                }
            }
            assumeTrue(false, "Host cannot create test symlinks: " + exception.getClass().getSimpleName());
        }
    }

    private static String validKey() {
        return "models/101/versions/1/files/550e8400-e29b-41d4-a716-446655440000.glb";
    }

    private static byte[] glb() {
        byte[] json = text("{\"asset\":{\"version\":\"2.0\"}}");
        int length = (json.length + 3) / 4 * 4;
        ByteBuffer buffer = ByteBuffer.allocate(20 + length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x46546c67).putInt(2).putInt(buffer.capacity()).putInt(length).putInt(0x4e4f534a).put(json);
        while (buffer.hasRemaining()) buffer.put((byte) ' ');
        return buffer.array();
    }

    private static byte[] binaryStl() {
        ByteBuffer buffer = ByteBuffer.allocate(134).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(80);
        buffer.putInt(1);
        for (float value : new float[] {0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0}) buffer.putFloat(value);
        buffer.putShort((short) 0);
        return buffer.array();
    }

    private static byte[] asciiStl() {
        return text("solid triangle\nfacet normal 0 0 1\nouter loop\nvertex 0 0 0\n"
                + "vertex 1 0 0\nvertex 0 1 0\nendloop\nendfacet\nendsolid triangle\n");
    }

    private static byte[] image(String format) throws IOException {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output));
        return output.toByteArray();
    }

    private static Map<String, byte[]> threeMfEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", text("<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                + "<Default Extension='model' ContentType='application/vnd.ms-package.3dmanufacturing-3dmodel+xml'/></Types>"));
        entries.put("_rels/.rels", text("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Target='/3D/3dmodel.model' Id='r1' Type='http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel'/></Relationships>"));
        entries.put("3D/3dmodel.model", text("<model unit='millimeter' xmlns='http://schemas.microsoft.com/3dmanufacturing/core/2015/02'>"
                + "<resources><object id='1' type='model'><mesh><vertices><vertex x='0' y='0' z='0'/>"
                + "<vertex x='1' y='0' z='0'/><vertex x='0' y='1' z='0'/></vertices>"
                + "<triangles><triangle v1='0' v2='1' v3='2'/></triangles></mesh></object></resources>"
                + "<build><item objectid='1'/></build></model>"));
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
