package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.entity.*;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.repository.*;
import com.project.w_3d_world.features.model.service.*;
import com.project.w_3d_world.features.model.service.impl.*;
import com.project.w_3d_world.features.model.storage.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Spring transaction lifecycle tests with a recording resource, NOT PostgreSQL integration tests. */
class ModelUploadServiceTest {
    @TempDir Path root;
    Model3DRepository models;
    ModelVersionRepository versions;
    ModelFileRepository files;
    ModelStorageService storage;
    RecordingTransactions transactions;
    ModelUploadService service;
    List<Model3D> savedModels;
    List<ModelVersion> savedVersions;
    List<ModelFile> savedFiles;

    @BeforeEach
    void setup() {
        models = mock(Model3DRepository.class);
        versions = mock(ModelVersionRepository.class);
        files = mock(ModelFileRepository.class);
        storage = spy(new LocalModelStorageService(root));
        transactions = new RecordingTransactions();
        savedModels = new ArrayList<>(); savedVersions = new ArrayList<>(); savedFiles = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(100);
        when(models.saveAndFlush(any())).thenAnswer(call -> {
            Model3D entity = call.getArgument(0);
            ReflectionTestUtils.setField(entity, "modelId", ids.incrementAndGet());
            savedModels.add(entity); return entity;
        });
        when(versions.saveAndFlush(any())).thenAnswer(call -> {
            ModelVersion entity = call.getArgument(0);
            ReflectionTestUtils.setField(entity, "modelVersionId", ids.incrementAndGet());
            savedVersions.add(entity); return entity;
        });
        when(files.saveAndFlush(any())).thenAnswer(call -> {
            ModelFile entity = call.getArgument(0);
            ReflectionTestUtils.setField(entity, "fileId", ids.incrementAndGet());
            savedFiles.add(entity); return entity;
        });
        service = proxy();
    }

    ModelUploadService proxy() {
        var target = new ModelUploadServiceImpl(models, versions, files, storage);
        ProxyFactory factory = new ProxyFactory(target);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (ModelUploadService) factory.getProxy();
    }

    @ParameterizedTest(name = "UP-01..04 real storage format {0}")
    @ValueSource(strings = {"glb", "stl", "obj", "3mf"})
    void uploadsValidatedFormats(String extension) throws Exception {
        byte[] bytes = fixture(extension);
        ModelUploadResponse response = service.uploadModel(7, request(), upload(extension, bytes));
        assertEquals(extension, response.fileFormat());
        assertEquals(bytes.length, response.fileSize());
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
        try (var stream = storage.open(savedFiles.getFirst().getStorageKey())) {
            assertArrayEquals(bytes, stream.readAllBytes());
        }
        verify(storage, never()).deleteForCleanup(anyString());
    }

    @Test
    void up18_up22_fixedMetadataAndScalarOwnership() throws Exception {
        var response = service.uploadModel(7, request(), upload("glb", fixture("glb")));
        assertEquals(1, savedModels.size()); assertEquals(1, savedVersions.size()); assertEquals(1, savedFiles.size());
        var model = savedModels.getFirst(); var version = savedVersions.getFirst(); var file = savedFiles.getFirst();
        assertEquals(7, model.getOwnerUserId()); assertEquals("Dragon", model.getModelName());
        assertEquals("description", model.getDescription()); assertEquals("PRIVATE", model.getVisibility());
        assertEquals(ModelType.WEB_EDITOR, model.getModelType()); assertEquals(ModelOriginType.UPLOAD, model.getOriginType());
        assertNull(model.getDeletedAt()); assertNull(model.getUpdatedAt()); assertNotNull(model.getCreatedAt());
        assertEquals(model.getModelId(), version.getModelId()); assertEquals(7, version.getCreatedByUserId());
        assertEquals(1, version.getVersionNumber()); assertEquals(ModelSourceType.UPLOAD, version.getSourceType());
        assertNull(version.getAiRequestId()); assertNull(version.getParentVersionId()); assertNull(version.getChangeNote());
        assertEquals(version.getModelVersionId(), file.getModelVersionId());
        assertEquals(ModelFileRole.SOURCE_MODEL, file.getFileRole()); assertTrue(file.isPrimary());
        assertEquals("model/gltf-binary", file.getMimeType()); assertTrue(file.getFileHash().matches("[0-9a-f]{64}"));
        assertEquals(file.getFileId(), response.fileId()); assertEquals(model.getModelId(), response.modelId());
        assertEquals(version.getModelVersionId(), response.modelVersionId());
    }

    @Test
    void up06_up07_invalidMetadataRejectedBeforePersistence() {
        for (var metadata : List.of(new CreateModelRequest("  ", null), new CreateModelRequest("x".repeat(256), null),
                new CreateModelRequest("bad\u0000name", null), new CreateModelRequest("name", "x".repeat(10001)),
                new CreateModelRequest("name", "bad\u0000description"))) {
            assertThrows(ApiException.class, () -> service.uploadModel(7, metadata, upload("glb", new byte[] {1})));
        }
        verifyNoInteractions(models, versions, files, storage);
    }

    @Test
    void up08_up09_missingAndEmptyFileRejected() {
        assertThrows(ApiException.class, () -> service.uploadModel(7, request(), null));
        assertThrows(ApiException.class, () -> service.uploadModel(7, request(), upload("glb", new byte[0])));
        verifyNoInteractions(models, versions, files, storage);
    }

    @Test
    void up10_unsupportedFormatRollsBack() throws Exception {
        assertThrows(ModelStorageException.class, () -> service.uploadModel(7, request(), upload("exe", new byte[] {1})));
        assertEquals(1, transactions.rollbacks); verifyNoInteractions(files); assertNoStoredFiles();
    }

    @Test
    void up11_sizeLimitUsesActualStreamNotDeclaredSize() throws Exception {
        var file = new MockMultipartFile("file", "large.glb", "application/octet-stream", new byte[] {1}) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    long remaining = 50_000_001;
                    @Override public int read() { return remaining-- > 0 ? 0 : -1; }
                    @Override public int read(byte[] b, int off, int len) {
                        if (remaining == 0) return -1;
                        int n = (int) Math.min(remaining, len); Arrays.fill(b, off, off + n, (byte) 0);
                        remaining -= n; return n;
                    }
                };
            }
        };
        var exception = assertThrows(ModelStorageException.class, () -> service.uploadModel(7, request(), file));
        assertEquals(ModelStorageException.Code.TOO_LARGE, exception.getCode());
        assertEquals(1, transactions.rollbacks); verifyNoInteractions(files); assertNoStoredFiles();
    }

    @Test
    void up12_invalidPrincipalIdRejected() {
        for (Integer id : Arrays.asList(null, 0, -1)) {
            assertThrows(ApiException.class, () -> service.uploadModel(id, request(), upload("glb", new byte[] {1})));
        }
        verifyNoInteractions(models, versions, files, storage);
    }

    @Test
    void up13_modelRepositoryFailureStopsUpload() {
        doThrow(new DataIntegrityViolationException("injected")).when(models).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadModel(7, request(), upload("glb", new byte[] {1})));
        assertEquals(1, transactions.rollbacks); verifyNoInteractions(versions, files, storage);
    }

    @Test
    void up14_versionRepositoryFailureTriggersRollbackWithoutStorage() {
        doThrow(new DataIntegrityViolationException("injected")).when(versions).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadModel(7, request(), upload("glb", new byte[] {1})));
        assertEquals(1, transactions.rollbacks); verifyNoInteractions(files, storage);
    }

    @Test
    void up15_fileRepositoryFailureCleansPublishedFileAfterRollback() throws Exception {
        doThrow(new DataIntegrityViolationException("injected")).when(files).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadModel(7, request(), upload("glb", fixture("glb"))));
        assertEquals(1, transactions.rollbacks); verify(storage).deleteForCleanup(anyString()); assertNoStoredFiles();
    }

    @Test
    void up16_storageFailureTriggersRollback() {
        doThrow(new ModelStorageException(ModelStorageException.Code.IO_FAILURE, "injected"))
                .when(storage).store(any(), any(), any(), any(), any(), any());
        assertThrows(ModelStorageException.class,
                () -> service.uploadModel(7, request(), upload("glb", new byte[] {1})));
        assertEquals(1, transactions.rollbacks); verifyNoInteractions(files);
        verify(storage, never()).deleteForCleanup(anyString());
    }

    @Test
    void modelPayloadIsStoredAsReceived() throws Exception {
        byte[] bytes = {1, 2, 3};
        service.uploadModel(7, request(), upload("glb", bytes));
        try (var input = storage.open(savedFiles.getFirst().getStorageKey())) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        assertEquals(1, transactions.commits);
    }

    @Test
    void up17_confirmedCommitFailureRollsBackAndCleans() throws Exception {
        transactions.failCommit = true;
        transactions.setRollbackOnCommitFailure(true);
        assertThrows(TransactionSystemException.class,
                () -> service.uploadModel(7, request(), upload("glb", fixture("glb"))));
        assertEquals(1, transactions.commitAttempts); assertEquals(1, transactions.rollbacks);
        verify(storage).deleteForCleanup(anyString()); assertNoStoredFiles();
    }

    @Test
    void up17_unknownCommitOutcomeKeepsFileAndReportsReconciliation() throws Exception {
        transactions.failCommit = true;
        transactions.setRollbackOnCommitFailure(false);
        assertThrows(TransactionSystemException.class,
                () -> service.uploadModel(7, request(), upload("glb", fixture("glb"))));
        verify(storage, never()).deleteForCleanup(anyString());
        assertTrue(Files.exists(root.resolve(savedFiles.getFirst().getStorageKey())));
    }

    @Test
    void cleanupFailureDoesNotMaskOriginalFailure() throws Exception {
        doThrow(new DataIntegrityViolationException("original failure")).when(files).saveAndFlush(any());
        doThrow(new ModelStorageException(ModelStorageException.Code.IO_FAILURE, "sensitive internal failure"))
                .when(storage).deleteForCleanup(anyString());
        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadModel(7, request(), upload("glb", fixture("glb"))));
        assertEquals("original failure", exception.getMessage()); assertEquals(1, transactions.rollbacks);
    }

    @Test
    void streamCloseFailureAfterPublishingStillCleansFile() throws Exception {
        var file = new MockMultipartFile("file", "model.glb", null, fixture("glb")) {
            @Override public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(getBytes()) {
                    @Override public void close() throws IOException { throw new IOException("injected close"); }
                };
            }
        };
        assertThrows(ModelStorageException.class, () -> service.uploadModel(7, request(), file));
        verify(storage).deleteForCleanup(anyString()); verifyNoInteractions(files); assertNoStoredFiles();
    }

    @Test
    void synchronizationRegisteredBeforeStoragePublication() throws Exception {
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            return call.callRealMethod();
        }).when(storage).store(any(), any(), any(), any(), any(), any());
        service.uploadModel(7, request(), upload("glb", fixture("glb")));
    }

    @Test
    void up20_sameNameTwoUsersHaveIndependentOwnership() throws Exception {
        var first = service.uploadModel(7, request(), upload("glb", fixture("glb")));
        var second = service.uploadModel(8, request(), upload("glb", fixture("glb")));
        assertNotEquals(first.modelId(), second.modelId());
        assertEquals(List.of(7, 8), savedModels.stream().map(Model3D::getOwnerUserId).toList());
        assertEquals(List.of(7, 8), savedVersions.stream().map(ModelVersion::getCreatedByUserId).toList());
        assertNotEquals(savedFiles.get(0).getStorageKey(), savedFiles.get(1).getStorageKey());
    }

    @Test
    void directInvocationWithoutTransactionFailsBeforeWrites() {
        var direct = new ModelUploadServiceImpl(models, versions, files, storage);
        assertThrows(IllegalStateException.class, () -> direct.uploadModel(7, request(), upload("glb", new byte[] {1})));
        verifyNoInteractions(models, versions, files, storage);
    }

    private void assertNoStoredFiles() throws IOException {
        try (var paths = Files.walk(root)) { assertEquals(0, paths.filter(Files::isRegularFile).count()); }
    }

    private static CreateModelRequest request() { return new CreateModelRequest("  Dragon  ", " description "); }
    private static MockMultipartFile upload(String ext, byte[] content) {
        return new MockMultipartFile("file", "model." + ext, null, content);
    }

    /** Minimal valid fixtures; real BE2-01.2 content validation is used, not mocked for success cases. */
    static byte[] fixture(String extension) throws IOException {
        if (extension.equals("glb")) {
            byte[] json = text("{\"asset\":{\"version\":\"2.0\"}}");
            int length = (json.length + 3) / 4 * 4;
            var b = ByteBuffer.allocate(20 + length).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(0x46546c67).putInt(2).putInt(b.capacity()).putInt(length).putInt(0x4e4f534a).put(json);
            while (b.hasRemaining()) b.put((byte) ' ');
            return b.array();
        }
        if (extension.equals("stl")) return text("solid triangle\nfacet normal 0 0 1\nouter loop\n"
                + "vertex 0 0 0\nvertex 1 0 0\nvertex 0 1 0\nendloop\nendfacet\nendsolid triangle\n");
        if (extension.equals("obj")) return text("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                + "<Default Extension='model' ContentType='application/vnd.ms-package.3dmanufacturing-3dmodel+xml'/></Types>");
        entries.put("_rels/.rels", "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                + "<Relationship Target='/3D/3dmodel.model' Id='r1' Type='http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel'/></Relationships>");
        entries.put("3D/3dmodel.model", "<model xmlns='http://schemas.microsoft.com/3dmanufacturing/core/2015/02'>"
                + "<resources><object id='1' type='model'><mesh><vertices><vertex x='0' y='0' z='0'/>"
                + "<vertex x='1' y='0' z='0'/><vertex x='0' y='1' z='0'/></vertices>"
                + "<triangles><triangle v1='0' v2='1' v3='2'/></triangles></mesh></object></resources>"
                + "<build><item objectid='1'/></build></model>");
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(text(entry.getValue())); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks, commitAttempts;
        boolean failCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {
            commitAttempts++;
            if (failCommit) throw new TransactionSystemException("injected commit failure");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
