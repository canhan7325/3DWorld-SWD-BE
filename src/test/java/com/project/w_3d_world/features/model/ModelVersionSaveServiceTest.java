package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.enums.ModelType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelVersionSaveService;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.service.impl.ModelDownloadServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionHistoryServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionSaveServiceImpl;
import com.project.w_3d_world.features.model.storage.ModelStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelVersionSaveServiceTest {
    @TempDir Path root;

    private Model3DRepository models;
    private ModelVersionRepository versions;
    private ModelFileRepository files;
    private LocalModelStorageService storage;
    private RecordingTransactions transactions;
    private ModelVersionSaveService service;
    private Model3D model;
    private ModelVersion original;
    private ModelFile originalFile;
    private List<ModelVersion> savedVersions;
    private List<ModelFile> savedFiles;
    private AtomicInteger nextVersionId;
    private AtomicInteger nextFileId;

    @BeforeEach
    void setUp() {
        models = mock(Model3DRepository.class);
        versions = mock(ModelVersionRepository.class);
        files = mock(ModelFileRepository.class);
        storage = spy(new LocalModelStorageService(root));
        transactions = new RecordingTransactions();
        nextVersionId = new AtomicInteger(41);
        nextFileId = new AtomicInteger(88);
        savedVersions = java.util.Collections.synchronizedList(new ArrayList<>());
        savedFiles = java.util.Collections.synchronizedList(new ArrayList<>());
        model = new Model3D(7, "Dragon", null, ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE");
        ReflectionTestUtils.setField(model, "modelId", 15);
        original = version(41, 1, null);
        originalFile = new ModelFile(41, ModelFileRole.SOURCE_MODEL, "stl", "old-key",
                "model/stl", "old-hash", 4L, true);
        ReflectionTestUtils.setField(originalFile, "fileId", 87);
        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelVersionIdAndModelId(anyInt(), eq(15))).thenAnswer(call -> {
            int id = call.getArgument(0);
            if (id == 41) return Optional.of(original);
            synchronized (savedVersions) {
                return savedVersions.stream().filter(value -> value.getModelVersionId() == id).findFirst();
            }
        });
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(15), any(Pageable.class)))
                .thenAnswer(call -> {
                    synchronized (savedVersions) {
                        Pageable pageable = call.getArgument(1);
                        List<ModelVersion> ordered = new ArrayList<>(savedVersions);
                        ordered.add(original);
                        ordered.sort(Comparator.comparingInt(ModelVersion::getVersionNumber).reversed());
                        int start = (int) Math.min(pageable.getOffset(), ordered.size());
                        int end = Math.min(start + pageable.getPageSize(), ordered.size());
                        return new PageImpl<>(ordered.subList(start, end), pageable, ordered.size());
                    }
                });
        when(versions.saveAndFlush(any())).thenAnswer(call -> {
            ModelVersion saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "modelVersionId", nextVersionId.incrementAndGet());
            savedVersions.add(saved);
            return saved;
        });
        when(files.saveAndFlush(any())).thenAnswer(call -> {
            ModelFile saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "fileId", nextFileId.incrementAndGet());
            savedFiles.add(saved);
            return saved;
        });
        var target = new ModelVersionSaveServiceImpl(models, versions, files, storage);
        var factory = new ProxyFactory(target);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        service = (ModelVersionSaveService) factory.getProxy();
    }

    @Test
    void ownerSavesSequentialVersionsWithoutChangingHistoricalRows() throws Exception {
        byte[] firstBytes = {1, 2, 3, 4};
        Instant before = Instant.now();
        var first = service.saveVersion(7, 15, new SaveModelVersionRequest(41, "First edit"),
                upload(firstBytes));
        var second = service.saveVersion(7, 15, new SaveModelVersionRequest(42, "Second edit"),
                upload(new byte[] {5, 6, 7}));
        Instant after = Instant.now();

        assertEquals(15, first.modelId());
        assertEquals(42, first.modelVersionId());
        assertEquals(2, first.versionNumber());
        assertEquals(41, first.parentVersionId());
        assertEquals(ModelSourceType.EDITOR_SAVE, first.sourceType());
        assertEquals("First edit", first.changeNote());
        assertFalse(first.createdAt().isBefore(before));
        assertFalse(first.createdAt().isAfter(after));
        assertEquals(3, second.versionNumber());
        assertEquals(42, second.parentVersionId());
        assertEquals(41, original.getModelVersionId());
        assertEquals(1, original.getVersionNumber());
        assertEquals("old-key", originalFile.getStorageKey());
        assertEquals(2, savedVersions.size());
        assertEquals(2, savedFiles.size());
        assertEquals(7, savedVersions.getFirst().getCreatedByUserId());
        assertNull(savedVersions.getFirst().getAiRequestId());
        assertEquals(42, savedFiles.getFirst().getModelVersionId());
        assertEquals(ModelFileRole.SOURCE_MODEL, savedFiles.getFirst().getFileRole());
        assertTrue(savedFiles.getFirst().isPrimary());
        assertNotEquals(savedFiles.get(0).getStorageKey(), savedFiles.get(1).getStorageKey());
        assertEquals(first.file().fileId(), savedFiles.getFirst().getFileId());
        assertEquals("/api/models/15/files/89/download", first.file().downloadPath());
        assertFalse(first.toString().contains(savedFiles.getFirst().getStorageKey()));
        try (var input = storage.open(savedFiles.getFirst().getStorageKey())) {
            assertArrayEquals(firstBytes, input.readAllBytes());
        }
        var order = inOrder(models, versions);
        order.verify(models).findOwnedActiveByIdForUpdate(15, 7);
        order.verify(versions).findByModelVersionIdAndModelId(41, 15);
        order.verify(versions).findByModelIdOrderByVersionNumberDesc(eq(15), any());
        assertEquals(2, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    void historyListsSavedVersionsNewestFirstAndHistoricalDownloadKeepsBytes() throws Exception {
        byte[] versionTwoBytes = {10, 20, 30};
        service.saveVersion(7, 15, new SaveModelVersionRequest(41, "v2"), upload(versionTwoBytes));
        service.saveVersion(7, 15, new SaveModelVersionRequest(42, "v3"), upload(new byte[]{40}));
        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(15, 7))
                .thenReturn(Optional.of(model));
        when(files.findByModelVersionIdOrderByFileIdAsc(42))
                .thenAnswer(call -> List.of(savedFiles.getFirst()));
        when(files.findById(89)).thenAnswer(call -> Optional.of(savedFiles.getFirst()));

        var history = new ModelVersionHistoryServiceImpl(models, versions, files);
        var page = history.getModelVersions(7, 15, 0, 20);
        assertEquals(List.of(3, 2, 1), page.content().stream()
                .map(entry -> entry.versionNumber()).toList());
        assertEquals("v3", page.content().getFirst().changeNote());
        assertEquals("v2", page.content().get(1).changeNote());
        var older = history.getModelVersionDetails(7, 15, 42);
        assertEquals(89, older.files().getFirst().fileId());
        assertEquals(42, older.modelVersionId());
        assertNotNull(older.createdAt());

        var download = new ModelDownloadServiceImpl(models, versions, files, storage);
        try (var handle = download.openAuthorizedDownload(7, 15, 89)) {
            assertArrayEquals(versionTwoBytes, handle.inputStream().readAllBytes());
            assertEquals("model-15-file-89.stl", handle.filename());
        }
        assertEquals(404, assertThrows(ApiException.class,
                () -> download.openAuthorizedDownload(8, 15, 89)).getStatus().value());
    }

    @Test
    void ownershipDeletionAndInvalidBaseStopBeforeWrites() {
        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.saveVersion(7, 15, new SaveModelVersionRequest(41, null), upload(new byte[] {1})))
                .getStatus().value());
        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelVersionIdAndModelId(99, 15)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.saveVersion(7, 15, new SaveModelVersionRequest(99, null), upload(new byte[] {1})))
                .getStatus().value());
        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.saveVersion(8, 15, new SaveModelVersionRequest(41, null), upload(new byte[] {1})))
                .getStatus().value());
        verify(versions, never()).saveAndFlush(any());
        verifyNoInteractions(files, storage);
    }

    @Test
    void staleBaseReturnsConflictWithoutWriting() {
        var current = version(42, 2, 41);
        savedVersions.add(current);
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.saveVersion(7, 15, new SaveModelVersionRequest(41, null), upload(new byte[] {1})))
                .getStatus().value());
        verify(versions, never()).saveAndFlush(any());
        verifyNoInteractions(files, storage);
    }

    @Test
    void storageFailureRollsBackWithoutPublishingFile() throws Exception {
        var invalid = upload(new byte[] {1, 2, 3});
        doThrow(new ModelStorageException(ModelStorageException.Code.IO_FAILURE, "disk failure"))
                .when(storage).store(any(), any(), any(), any(), any(), any());
        assertThrows(ModelStorageException.class,
                () -> service.saveVersion(7, 15, new SaveModelVersionRequest(41, null), invalid));
        assertEquals(1, transactions.rollbacks);
        verifyNoInteractions(files);
        assertNoFiles();
    }

    @Test
    void filePersistenceFailureCleansNewFileAfterRollback() throws Exception {
        doThrow(new DataIntegrityViolationException("failure")).when(files).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> service.saveVersion(7, 15, new SaveModelVersionRequest(41, null),
                        upload(new byte[] {4, 5, 6})));
        assertEquals(1, transactions.rollbacks);
        verify(storage).deleteForCleanup(anyString());
        assertNoFiles();
    }

    @Test
    void oversizedStreamIsRejectedAndRolledBack() throws Exception {
        var large = new MockMultipartFile("file", "large.stl", "model/stl", new byte[] {1}) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    long remaining = 50_000_001;
                    @Override public int read() { return remaining-- > 0 ? 0 : -1; }
                    @Override public int read(byte[] buffer, int offset, int length) {
                        if (remaining == 0) return -1;
                        int count = (int) Math.min(remaining, length);
                        java.util.Arrays.fill(buffer, offset, offset + count, (byte) 0);
                        remaining -= count;
                        return count;
                    }
                };
            }
        };
        var error = assertThrows(ModelStorageException.class,
                () -> service.saveVersion(7, 15, new SaveModelVersionRequest(41, null), large));
        assertEquals(ModelStorageException.Code.TOO_LARGE, error.getCode());
        assertEquals(1, transactions.rollbacks);
        verifyNoInteractions(files);
        assertNoFiles();
    }

    @Test
    void serializedConcurrentSavesRejectOneStaleBase() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    service.saveVersion(7, 15, new SaveModelVersionRequest(41, null),
                            upload(new byte[] {(byte) index}));
                    return 201;
                } catch (ApiException error) {
                    return error.getStatus().value();
                }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(List.of(201, 409), attempts.stream().map(future -> {
                try { return future.get(10, TimeUnit.SECONDS); }
                catch (Exception error) { throw new AssertionError(error); }
            }).sorted().toList());
        }
        assertEquals(1, savedVersions.size());
        assertEquals(1, savedFiles.size());
    }

    private static ModelVersion version(int id, int number, Integer parentId) {
        var version = new ModelVersion(15, parentId, null, 7, number, ModelSourceType.UPLOAD, null);
        ReflectionTestUtils.setField(version, "modelVersionId", id);
        return version;
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "Dragon.stl", "model/stl", bytes);
    }

    private void assertNoFiles() throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    private static final class RecordingTransactions extends AbstractPlatformTransactionManager {
        private final ReentrantLock lock = new ReentrantLock();
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { lock.lock(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; lock.unlock(); }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; lock.unlock(); }
    }
}
