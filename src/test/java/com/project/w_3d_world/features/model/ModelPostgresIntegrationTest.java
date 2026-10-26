package com.project.w_3d_world.features.model;

import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.dto.request.RestoreModelVersionRequest;
import com.project.w_3d_world.features.model.dto.request.SaveModelVersionRequest;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.service.ModelVersionHistoryService;
import com.project.w_3d_world.features.model.service.ModelVersionRestoreService;
import com.project.w_3d_world.features.model.service.ModelVersionSaveService;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.service.impl.ModelDownloadServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelUploadServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionHistoryServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionRestoreServiceImpl;
import com.project.w_3d_world.features.model.service.impl.ModelVersionSaveServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "model.pg.integration", matches = "true")
@SpringBootTest(classes = ModelPostgresIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelPostgresIntegrationTest {
    private static final String ISOLATED_URL =
            "jdbc:postgresql://127.0.0.1:55439/model_restore_it";
    private static final String OFFICIAL_SHA256 =
            "24256de01f5721ec19317277c4619f3cc80edad9f44b5b9986b5410941faef37";

    @Autowired JdbcTemplate jdbc;
    @Autowired ModelUploadService upload;
    @Autowired ModelVersionSaveService save;
    @Autowired ModelVersionRestoreService restore;
    @Autowired ModelVersionHistoryService history;
    @Autowired ModelDownloadService download;
    @Autowired Model3DRepository models;
    @Autowired ModelVersionRepository versions;
    @Autowired ModelFileRepository files;
    @Autowired ModelStorageService storage;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry properties) {
        String url = System.getProperty("model.pg.jdbcUrl");
        if (!ISOLATED_URL.equals(url)) {
            throw new IllegalStateException("Model integration tests require the exact disposable PostgreSQL URL.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "modeltest");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.flyway.enabled", () -> "false");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.main.web-application-type", () -> "none");
    }

    @BeforeAll
    void verifyDisposableDatabaseAndOfficialSchema() throws Exception {
        Path workspaceTarget = Path.of(System.getProperty("user.dir"), "target").toRealPath();
        Path expectedData = Path.of(System.getProperty("model.pg.dataDir")).toRealPath();
        assertTrue(expectedData.startsWith(workspaceTarget));
        assertEquals(expectedData, Path.of(jdbc.queryForObject(
                "select current_setting('data_directory')", String.class)).toRealPath());
        assertEquals("model_restore_it", jdbc.queryForObject("select current_database()", String.class));
        assertEquals(55439, jdbc.queryForObject("select inet_server_port()", Integer.class));
        assertEquals(36, jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema='public' and table_type='BASE TABLE'", Integer.class));
        byte[] official = Files.readAllBytes(Path.of(System.getProperty("model.pg.schemaPath")));
        assertEquals(OFFICIAL_SHA256, HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(official)));
    }

    @Test
    void validatesOfficialSchemaWithRealPostgres() {
        assertTrue(jdbc.queryForObject("select current_setting('server_version_num')::int",
                Integer.class) >= 170000);
    }

    @Test
    void uploadSaveRestorePersistsIndependentHistoryAndAuthorizedBytes() throws Exception {
        int owner = newUser();
        int other = newUser();
        byte[] original = new byte[]{1, 2, 3, 4};
        byte[] edited = new byte[]{5, 6, 7, 8};
        var first = upload.uploadModel(owner, new CreateModelRequest("Dragon", null),
                stl(original));
        int modelId = first.modelId();
        int firstVersionId = first.modelVersionId();
        var firstFile = files.findById(first.fileId()).orElseThrow();
        String firstKey = firstFile.getStorageKey();
        Instant firstCreatedAt = versions.findById(firstVersionId).orElseThrow().getCreatedAt();

        byte[] scene = "{\"objects\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var sceneObject = storage.store(new ByteArrayInputStream(scene), "scene.json",
                "application/json", ModelFileRole.EDITABLE_SCENE, modelId, firstVersionId);
        var sceneFile = files.saveAndFlush(new ModelFile(firstVersionId,
                ModelFileRole.EDITABLE_SCENE, sceneObject.fileFormat(), sceneObject.storageKey(),
                sceneObject.mimeType(), sceneObject.fileHash(), sceneObject.fileSize(), false));

        var second = save.saveVersion(owner, modelId,
                new SaveModelVersionRequest(firstVersionId, "Edited"), stl(edited));
        int secondVersionId = second.modelVersionId();
        var secondFile = files.findById(second.file().fileId()).orElseThrow();
        String secondKey = secondFile.getStorageKey();
        var third = restore.restoreVersion(owner, modelId, firstVersionId,
                new RestoreModelVersionRequest(secondVersionId, null));

        assertEquals(modelId, third.modelId());
        assertEquals(3, third.versionNumber());
        assertEquals(firstVersionId, third.parentVersionId());
        assertEquals(2, third.files().size());
        assertTrue(models.findById(modelId).isPresent());
        assertEquals(List.of(3, 2, 1), history.getModelVersions(owner, modelId, 0, 20)
                .content().stream().map(value -> value.versionNumber()).toList());
        assertEquals(2, history.getModelVersionDetails(owner, modelId, third.modelVersionId())
                .files().size());
        assertNotEquals(firstKey, files.findById(third.files().getFirst().fileId())
                .orElseThrow().getStorageKey());
        assertNotEquals(sceneFile.getStorageKey(), files.findById(third.files().get(1).fileId())
                .orElseThrow().getStorageKey());
        assertEquals(firstKey, files.findById(first.fileId()).orElseThrow().getStorageKey());
        assertEquals(secondKey, files.findById(second.file().fileId()).orElseThrow().getStorageKey());
        assertEquals(firstCreatedAt, versions.findById(firstVersionId).orElseThrow().getCreatedAt());
        assertNotNull(versions.findById(third.modelVersionId()).orElseThrow().getCreatedAt());
        assertEquals(3, jdbc.queryForObject(
                "select count(*) from model_versions where model_id=?", Integer.class, modelId));
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from model_files where model_version_id=?", Integer.class,
                third.modelVersionId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from model_files "
                + "where model_version_id=? and (file_hash is null or file_size is null "
                + "or storage_key is null)", Integer.class, third.modelVersionId()));
        var restoredFile = files.findById(third.files().getFirst().fileId()).orElseThrow();
        assertEquals(original.length, restoredFile.getFileSize());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(original)), restoredFile.getFileHash());
        try (var originalDownload = download.openAuthorizedDownload(owner, modelId, first.fileId());
                var editedDownload = download.openAuthorizedDownload(owner, modelId,
                        second.file().fileId());
                var restoredDownload = download.openAuthorizedDownload(owner, modelId,
                        third.files().getFirst().fileId());
                var sceneDownload = download.openAuthorizedDownload(owner, modelId,
                        third.files().get(1).fileId())) {
            assertArrayEquals(original, originalDownload.inputStream().readAllBytes());
            assertArrayEquals(edited, editedDownload.inputStream().readAllBytes());
            assertArrayEquals(original, restoredDownload.inputStream().readAllBytes());
            assertArrayEquals(scene, sceneDownload.inputStream().readAllBytes());
        }
        assertEquals(404, assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> restore.restoreVersion(other, modelId, firstVersionId,
                        new RestoreModelVersionRequest(third.modelVersionId(), null)))
                .getStatus().value());
        assertEquals(404, assertThrows(com.project.w_3d_world.common.exception.ApiException.class,
                () -> download.openAuthorizedDownload(other, modelId, first.fileId()))
                .getStatus().value());
    }

    @Test
    void printRequestKeepsItsSelectedVersionAcrossSaveAndRestore() throws Exception {
        int owner = newUser();
        byte[] original = new byte[]{11, 22, 33};
        var first = upload.uploadModel(owner, new CreateModelRequest("Pinned source", null),
                stl(original));
        var firstFile = files.findById(first.fileId()).orElseThrow();
        String originalKey = firstFile.getStorageKey();
        String originalHash = firstFile.getFileHash();

        int materialId = jdbc.queryForObject("insert into print_materials "
                + "(material_name,technology_code) values (?,?) returning material_id",
                Integer.class, "PLA", "FDM");
        int requestId = jdbc.queryForObject("insert into print_requests "
                + "(customer_user_id) values (?) returning print_request_id",
                Integer.class, owner);
        int itemId = jdbc.queryForObject("insert into print_request_items "
                + "(print_request_id,model_version_id,material_id,quantity) "
                + "values (?,?,?,?) returning print_request_item_id",
                Integer.class, requestId, first.modelVersionId(), materialId, 1);

        var second = save.saveVersion(owner, first.modelId(),
                new SaveModelVersionRequest(first.modelVersionId(), "Edited after Print Request"),
                stl(new byte[]{44, 55, 66}));
        var third = restore.restoreVersion(owner, first.modelId(), first.modelVersionId(),
                new RestoreModelVersionRequest(second.modelVersionId(), "Restore original"));

        assertEquals(3, third.versionNumber());
        assertEquals(first.modelVersionId(), jdbc.queryForObject(
                "select model_version_id from print_request_items where print_request_item_id=?",
                Integer.class, itemId));
        assertEquals(originalKey, files.findById(first.fileId()).orElseThrow().getStorageKey());
        assertEquals(originalHash, files.findById(first.fileId()).orElseThrow().getFileHash());
        assertEquals(1, versions.findById(first.modelVersionId())
                .orElseThrow().getVersionNumber());
        try (var historical = download.openAuthorizedDownload(owner, first.modelId(), first.fileId())) {
            assertArrayEquals(original, historical.inputStream().readAllBytes());
        }
    }

    @Test
    void postgresEnforcesVersionParentSourcePrimaryAndFileForeignKeys() {
        int owner = newUser();
        var first = upload.uploadModel(owner, new CreateModelRequest("One", null),
                stl(new byte[]{1}));
        var anotherModel = upload.uploadModel(owner, new CreateModelRequest("Two", null),
                stl(new byte[]{2}));
        String insertVersion = "insert into model_versions "
                + "(model_id,parent_version_id,created_by_user_id,version_number,source_type) "
                + "values (?,?,?,?,?)";
        assertThrows(DataAccessException.class, () -> jdbc.update(insertVersion,
                first.modelId(), null, owner, 1, "EDITOR_SAVE"));
        assertThrows(DataAccessException.class, () -> jdbc.update(insertVersion,
                first.modelId(), anotherModel.modelVersionId(), owner, 2, "EDITOR_SAVE"));
        assertThrows(DataAccessException.class, () -> jdbc.update(insertVersion,
                first.modelId(), null, owner, 2, "INVALID_SOURCE"));
        assertThrows(DataAccessException.class, () -> jdbc.update(insertVersion,
                first.modelId(), null, owner, 2, "AI_GENERATED"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into model_files (model_version_id,file_role,file_format,storage_key,is_primary) "
                        + "values (?,?,?,?,true)", first.modelVersionId(), "SOURCE_MODEL", "stl",
                "another-primary"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into model_files (model_version_id,file_role,file_format,storage_key,is_primary) "
                        + "values (?,?,?,?,false)", 999_999_999, "SOURCE_MODEL", "stl",
                "missing-version"));

        String insertAi = "insert into ai_requests (user_id,model_id,base_version_id,request_type) "
                + "values (?,?,?,?) returning ai_request_id";
        int aiRequest = jdbc.queryForObject(insertAi, Integer.class, owner,
                first.modelId(), first.modelVersionId(), "TEXT_TO_3D");
        int otherModelAiRequest = jdbc.queryForObject(insertAi, Integer.class, owner,
                anotherModel.modelId(), anotherModel.modelVersionId(), "TEXT_TO_3D");
        String insertAiVersion = "insert into model_versions "
                + "(model_id,parent_version_id,ai_request_id,created_by_user_id,version_number,source_type) "
                + "values (?,?,?,?,?,'AI_GENERATED') returning model_version_id";
        assertNotNull(jdbc.queryForObject(insertAiVersion, Integer.class,
                first.modelId(), first.modelVersionId(), aiRequest, owner, 2));
        assertThrows(DataAccessException.class, () -> jdbc.queryForObject(insertAiVersion,
                Integer.class, first.modelId(), first.modelVersionId(), aiRequest, owner, 3));
        assertThrows(DataAccessException.class, () -> jdbc.queryForObject(insertAiVersion,
                Integer.class, first.modelId(), first.modelVersionId(), otherModelAiRequest, owner, 3));
    }

    @Test
    void concurrentSavesSerializeAndRejectOneStaleBase() throws Exception {
        int owner = newUser();
        var first = upload.uploadModel(owner, new CreateModelRequest("Concurrent", null),
                stl(new byte[]{1}));
        var results = race(
                () -> save.saveVersion(owner, first.modelId(),
                        new SaveModelVersionRequest(first.modelVersionId(), "A"),
                        stl(new byte[]{2})),
                () -> save.saveVersion(owner, first.modelId(),
                        new SaveModelVersionRequest(first.modelVersionId(), "B"),
                        stl(new byte[]{3})));
        assertEquals(List.of(201, 409), results);
        assertEquals(List.of(2, 1), history.getModelVersions(owner, first.modelId(), 0, 20)
                .content().stream().map(value -> value.versionNumber()).toList());
    }

    @Test
    void concurrentSaveAndRestoreSerializeWithoutOverwritingHistory() throws Exception {
        int owner = newUser();
        var first = upload.uploadModel(owner, new CreateModelRequest("Concurrent restore", null),
                stl(new byte[]{1}));
        var second = save.saveVersion(owner, first.modelId(),
                new SaveModelVersionRequest(first.modelVersionId(), "v2"), stl(new byte[]{2}));
        var results = race(
                () -> save.saveVersion(owner, first.modelId(),
                        new SaveModelVersionRequest(second.modelVersionId(), "v3"),
                        stl(new byte[]{3})),
                () -> restore.restoreVersion(owner, first.modelId(), first.modelVersionId(),
                        new RestoreModelVersionRequest(second.modelVersionId(), "restore v1")));
        assertEquals(List.of(201, 409), results);
        assertEquals(List.of(3, 2, 1), history.getModelVersions(owner, first.modelId(), 0, 20)
                .content().stream().map(value -> value.versionNumber()).toList());
        try (var old = download.openAuthorizedDownload(owner, first.modelId(), first.fileId())) {
            assertArrayEquals(new byte[]{1}, old.inputStream().readAllBytes());
        }
    }

    @Test
    void injectedFilePersistenceFailureRollsBackRealPostgresAndCleansCopiedBytes() throws Exception {
        int owner = newUser();
        var first = upload.uploadModel(owner, new CreateModelRequest("Rollback", null),
                stl(new byte[]{1, 2, 3}));
        int originalVersions = jdbc.queryForObject(
                "select count(*) from model_versions where model_id=?", Integer.class,
                first.modelId());
        long originalFiles = storedFileCount();
        ModelFileRepository failingFiles = mock(ModelFileRepository.class, delegatesTo(files));
        doThrow(new DataIntegrityViolationException("injected file persistence failure"))
                .when(failingFiles).saveAndFlush(any());
        var target = new ModelVersionRestoreServiceImpl(models, versions, failingFiles, storage);
        var proxy = new ProxyFactory(target);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(interceptor);
        var failingRestore = (ModelVersionRestoreService) proxy.getProxy();

        assertThrows(DataIntegrityViolationException.class,
                () -> failingRestore.restoreVersion(owner, first.modelId(), first.modelVersionId(),
                        new RestoreModelVersionRequest(first.modelVersionId(), null)));
        assertEquals(originalVersions, jdbc.queryForObject(
                "select count(*) from model_versions where model_id=?", Integer.class,
                first.modelId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from model_files "
                + "where model_version_id=?", Integer.class, first.modelVersionId()));
        assertEquals(originalFiles, storedFileCount());
        try (var old = download.openAuthorizedDownload(owner, first.modelId(), first.fileId())) {
            assertArrayEquals(new byte[]{1, 2, 3}, old.inputStream().readAllBytes());
        }
    }

    private int newUser() {
        return jdbc.queryForObject("insert into users (email,password_hash,full_name) "
                + "values (?,?,?) returning user_id", Integer.class,
                "model-it-" + UUID.randomUUID() + "@example.invalid", "test-only-hash", "Model IT");
    }

    private static MockMultipartFile stl(byte[] bytes) {
        return new MockMultipartFile("file", "model.stl", "model/stl", bytes);
    }

    private long storedFileCount() throws Exception {
        Path root = Path.of(System.getProperty("model.pg.storageRoot"));
        if (!Files.exists(root)) return 0;
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static List<Integer> race(ThrowingTask first, ThrowingTask second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try { task.run(); return 201; }
                catch (com.project.w_3d_world.common.exception.ApiException error) {
                    return error.getStatus().value();
                }
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return futures.stream().map(future -> {
                try { return future.get(20, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).sorted().toList();
        }
    }

    @FunctionalInterface
    private interface ThrowingTask { void run() throws Exception; }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Model3D.class)
    @EnableJpaRepositories(basePackageClasses = {Model3DRepository.class,
            ModelVersionRepository.class, ModelFileRepository.class})
    @Import({ModelUploadServiceImpl.class, ModelVersionSaveServiceImpl.class,
            ModelVersionRestoreServiceImpl.class, ModelVersionHistoryServiceImpl.class,
            ModelDownloadServiceImpl.class})
    static class Config {
        @Bean ModelStorageService modelStorageService() {
            return new LocalModelStorageService(Path.of(System.getProperty("model.pg.storageRoot")));
        }
    }
}
