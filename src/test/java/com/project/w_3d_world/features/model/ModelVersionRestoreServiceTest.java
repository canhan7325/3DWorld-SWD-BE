package com.project.w_3d_world.features.model;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
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
import com.project.w_3d_world.features.model.service.ModelVersionRestoreService;
import com.project.w_3d_world.features.model.service.ModelVersionSaveService;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.service.impl.ModelDownloadServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionHistoryServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionRestoreServiceImpl;
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

class ModelVersionRestoreServiceTest {
    @TempDir Path root;

    private Model3DRepository models;
    private ModelVersionRepository versions;
    private ModelFileRepository files;
    private LocalModelStorageService storage;
    private ModelVersionRestoreService restore;
    private RecordingTransactions transactions;
    private Model3D model;
    private ModelVersion source;
    private ModelVersion current;
    private ModelFile sourceFile;
    private ModelFile sceneFile;
    private List<ModelVersion> savedVersions;
    private List<ModelFile> savedFiles;
    private byte[] sourceBytes;
    private byte[] sceneBytes;

    @BeforeEach
    void setUp() {
        models = mock(Model3DRepository.class);
        versions = mock(ModelVersionRepository.class);
        files = mock(ModelFileRepository.class);
        storage = spy(new LocalModelStorageService(root));
        transactions = new RecordingTransactions();
        savedVersions = java.util.Collections.synchronizedList(new ArrayList<>());
        savedFiles = java.util.Collections.synchronizedList(new ArrayList<>());
        model = new Model3D(7, "Dragon", null, ModelType.WEB_EDITOR, ModelOriginType.UPLOAD, "PRIVATE");
        ReflectionTestUtils.setField(model, "modelId", 15);
        source = version(41, 1, null);
        current = version(42, 2, 41);
        sourceBytes = new byte[]{1, 2, 3, 4};
        sceneBytes = "{\"objects\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var originalSource = storage.store(new ByteArrayInputStream(sourceBytes), "dragon.stl",
                "model/stl", ModelFileRole.SOURCE_MODEL, 15, 41);
        var originalScene = storage.store(new ByteArrayInputStream(sceneBytes), "scene.json",
                "application/json", ModelFileRole.EDITABLE_SCENE, 15, 41);
        sourceFile = file(87, ModelFileRole.SOURCE_MODEL, originalSource, true);
        sceneFile = file(88, ModelFileRole.EDITABLE_SCENE, originalScene, false);

        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelVersionIdAndModelId(anyInt(), eq(15))).thenAnswer(call -> {
            int id = call.getArgument(0);
            if (id == 41) return Optional.of(source);
            if (id == 42) return Optional.of(current);
            synchronized (savedVersions) {
                return savedVersions.stream().filter(value -> value.getModelVersionId() == id).findFirst();
            }
        });
        when(versions.findByModelIdOrderByVersionNumberDesc(eq(15), any(Pageable.class)))
                .thenAnswer(call -> {
                    synchronized (savedVersions) {
                        Pageable pageable = call.getArgument(1);
                        List<ModelVersion> ordered = new ArrayList<>(savedVersions);
                        ordered.addAll(List.of(source, current));
                        ordered.sort(Comparator.comparingInt(ModelVersion::getVersionNumber).reversed());
                        int start = (int) Math.min(pageable.getOffset(), ordered.size());
                        int end = Math.min(start + pageable.getPageSize(), ordered.size());
                        return new PageImpl<>(ordered.subList(start, end), pageable, ordered.size());
                    }
                });
        var nextVersionId = new AtomicInteger(42);
        when(versions.saveAndFlush(any())).thenAnswer(call -> {
            ModelVersion value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "modelVersionId", nextVersionId.incrementAndGet());
            savedVersions.add(value);
            return value;
        });
        when(files.findByModelVersionIdOrderByFileIdAsc(41))
                .thenReturn(List.of(sourceFile, sceneFile));
        var nextFileId = new AtomicInteger(88);
        when(files.saveAndFlush(any())).thenAnswer(call -> {
            ModelFile value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "fileId", nextFileId.incrementAndGet());
            savedFiles.add(value);
            return value;
        });
        restore = transactional(new ModelVersionRestoreServiceImpl(models, versions, files, storage),
                ModelVersionRestoreService.class);
    }

    @Test
    void restoresAllRolesIntoIndependentFilesAndPreservesHistory() throws Exception {
        Instant before = Instant.now();
        var result = restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, null));
        Instant after = Instant.now();

        assertEquals(15, result.modelId());
        assertEquals(43, result.modelVersionId());
        assertEquals(3, result.versionNumber());
        assertEquals(41, result.parentVersionId());
        assertEquals(ModelSourceType.EDITOR_SAVE, result.sourceType());
        assertEquals("Restored from version 1", result.changeNote());
        assertFalse(result.createdAt().isBefore(before));
        assertFalse(result.createdAt().isAfter(after));
        assertEquals(2, result.files().size());
        assertEquals(List.of(ModelFileRole.SOURCE_MODEL, ModelFileRole.EDITABLE_SCENE),
                result.files().stream().map(value -> value.fileRole()).toList());
        assertEquals(List.of(true, false), result.files().stream()
                .map(value -> value.isPrimary()).toList());
        assertEquals("/api/models/15/files/89/download", result.files().getFirst().downloadPath());
        assertFalse(result.toString().contains(sourceFile.getStorageKey()));
        assertEquals(43, savedFiles.getFirst().getModelVersionId());
        assertNotEquals(sourceFile.getStorageKey(), savedFiles.getFirst().getStorageKey());
        assertNotEquals(sceneFile.getStorageKey(), savedFiles.get(1).getStorageKey());
        assertEquals(sourceFile.getFileHash(), savedFiles.getFirst().getFileHash());
        assertEquals(sourceFile.getMimeType(), savedFiles.getFirst().getMimeType());
        try (var input = storage.open(savedFiles.getFirst().getStorageKey())) {
            assertArrayEquals(sourceBytes, input.readAllBytes());
        }
        try (var input = storage.open(savedFiles.get(1).getStorageKey())) {
            assertArrayEquals(sceneBytes, input.readAllBytes());
        }
        assertEquals(4, fileCount());
        assertEquals(1, source.getVersionNumber());
        assertEquals(2, current.getVersionNumber());
        assertEquals(41, sourceFile.getModelVersionId());
        assertEquals(42, current.getModelVersionId());
        assertEquals(1, transactions.commits);

        when(models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(15, 7))
                .thenReturn(Optional.of(model));
        when(files.findByModelVersionIdOrderByFileIdAsc(43))
                .thenReturn(List.copyOf(savedFiles));
        when(files.findById(89)).thenReturn(Optional.of(savedFiles.getFirst()));
        var history = new ModelVersionHistoryServiceImpl(models, versions, files);
        assertEquals(List.of(3, 2, 1), history.getModelVersions(7, 15, 0, 20)
                .content().stream().map(value -> value.versionNumber()).toList());
        assertEquals(2, history.getModelVersionDetails(7, 15, 43).files().size());
        var download = new ModelDownloadServiceImpl(models, versions, files, storage);
        try (var opened = download.openAuthorizedDownload(7, 15, 89)) {
            assertArrayEquals(sourceBytes, opened.inputStream().readAllBytes());
        }
        assertEquals(404, assertThrows(ApiException.class,
                () -> download.openAuthorizedDownload(8, 15, 89)).getStatus().value());
    }

    @Test
    void rejectsOtherOwnerDeletedModelAndWrongModelSource() throws Exception {
        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.empty());
        assertStatus(404, () -> restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, null)));
        when(models.findOwnedActiveByIdForUpdate(15, 7)).thenReturn(Optional.of(model));
        when(versions.findByModelVersionIdAndModelId(99, 15)).thenReturn(Optional.empty());
        assertStatus(404, () -> restore.restoreVersion(7, 15, 99,
                new RestoreModelVersionRequest(42, null)));
        assertStatus(404, () -> restore.restoreVersion(8, 15, 41,
                new RestoreModelVersionRequest(42, null)));
        verify(versions, never()).saveAndFlush(any());
        assertEquals(2, fileCount());
    }

    @Test
    void staleBaseAndUnusablePrimaryCannotPublishVersion() throws Exception {
        assertStatus(409, () -> restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(41, null)));
        when(files.findByModelVersionIdOrderByFileIdAsc(41)).thenReturn(List.of(sceneFile));
        assertStatus(409, () -> restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, null)));
        verify(versions, never()).saveAndFlush(any());
        assertEquals(2, fileCount());
    }

    @Test
    void missingHistoricalContentRollsBackWithoutDeletingOriginals() throws Exception {
        doThrow(new ModelStorageException(ModelStorageException.Code.NOT_FOUND, "missing"))
                .when(storage).open(sourceFile.getStorageKey());
        assertThrows(ModelStorageException.class, () -> restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, null)));
        assertEquals(1, transactions.rollbacks);
        assertEquals(2, fileCount());
        verify(files, never()).saveAndFlush(any());
    }

    @Test
    void laterHistoricalReadFailureCleansEarlierCopiedFile() throws Exception {
        doThrow(new ModelStorageException(ModelStorageException.Code.NOT_FOUND, "missing"))
                .when(storage).open(sceneFile.getStorageKey());
        assertThrows(ModelStorageException.class, () -> restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, null)));
        assertEquals(1, transactions.rollbacks);
        verify(storage).deleteForCleanup(anyString());
        assertEquals(2, fileCount());
        try (var input = storage.open(sourceFile.getStorageKey())) {
            assertArrayEquals(sourceBytes, input.readAllBytes());
        }
    }

    @Test
    void persistenceFailureCleansEveryNewObjectAfterRollback() throws Exception {
        var writes = new AtomicInteger();
        doAnswer(call -> {
            if (writes.incrementAndGet() == 2) {
                throw new DataIntegrityViolationException("failure after second storage copy");
            }
            ModelFile value = call.getArgument(0);
            ReflectionTestUtils.setField(value, "fileId", 89);
            return value;
        }).when(files).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class,
                () -> restore.restoreVersion(7, 15, 41,
                        new RestoreModelVersionRequest(42, "return to v1")));
        assertEquals(1, transactions.rollbacks);
        verify(storage, times(2)).deleteForCleanup(anyString());
        assertEquals(2, fileCount());
        try (var input = storage.open(sourceFile.getStorageKey())) {
            assertArrayEquals(sourceBytes, input.readAllBytes());
        }
    }

    @Test
    void historicalHashMismatchRollsBackTheCopiedObject() throws Exception {
        var inconsistent = new ModelFile(41, ModelFileRole.SOURCE_MODEL, "stl",
                sourceFile.getStorageKey(), "model/stl", "wrong-hash", 4L, true);
        ReflectionTestUtils.setField(inconsistent, "fileId", 87);
        when(files.findByModelVersionIdOrderByFileIdAsc(41))
                .thenReturn(List.of(inconsistent, sceneFile));
        assertStatus(500, () -> restore.restoreVersion(7, 15, 41,
                new RestoreModelVersionRequest(42, null)));
        assertEquals(1, transactions.rollbacks);
        assertEquals(2, fileCount());
        verify(storage).deleteForCleanup(anyString());
    }

    @Test
    void concurrentSaveAndRestoreCannotReuseTheSameVersionNumber() throws Exception {
        ModelVersionSaveService save = transactional(
                new ModelVersionSaveServiceImpl(models, versions, files, storage),
                ModelVersionSaveService.class);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(
                    executor.submit(() -> {
                        ready.countDown(); start.await();
                        try {
                            save.saveVersion(7, 15, new SaveModelVersionRequest(42, "edited"),
                                    new MockMultipartFile("file", "edited.stl", "model/stl",
                                            new byte[]{9, 8, 7}));
                            return 201;
                        } catch (ApiException error) { return error.getStatus().value(); }
                    }),
                    executor.submit(() -> {
                        ready.countDown(); start.await();
                        try {
                            restore.restoreVersion(7, 15, 41,
                                    new RestoreModelVersionRequest(42, "restored"));
                            return 201;
                        } catch (ApiException error) { return error.getStatus().value(); }
                    }));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(List.of(201, 409), attempts.stream().map(future -> {
                try { return future.get(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).sorted().toList());
        }
        assertEquals(1, savedVersions.size());
        assertEquals(3, savedVersions.getFirst().getVersionNumber());
        assertEquals(1, source.getVersionNumber());
        assertEquals(2, current.getVersionNumber());
    }

    private long fileCount() throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static ModelVersion version(int id, int number, Integer parentId) {
        var value = new ModelVersion(15, parentId, null, 7, number, ModelSourceType.UPLOAD, null);
        ReflectionTestUtils.setField(value, "modelVersionId", id);
        return value;
    }

    private static ModelFile file(int id, ModelFileRole role,
            com.project.w_3d_world.features.model.storage.StoredModelFile stored, boolean primary) {
        var value = new ModelFile(41, role, stored.fileFormat(), stored.storageKey(),
                stored.mimeType(), stored.fileHash(), stored.fileSize(), primary);
        ReflectionTestUtils.setField(value, "fileId", id);
        return value;
    }

    private <T> T transactional(Object target, Class<T> contract) {
        var factory = new ProxyFactory(target);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return contract.cast(factory.getProxy());
    }

    private static void assertStatus(int expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ApiException.class, action).getStatus().value());
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
