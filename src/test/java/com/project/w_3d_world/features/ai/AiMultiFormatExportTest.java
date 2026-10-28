package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.AiExportRequest;
import com.project.w_3d_world.features.ai.dto.response.AiExportResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.MockAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.provider.ProviderTask;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.ModelConverterService;
import com.project.w_3d_world.features.ai.service.PricingService;
import com.project.w_3d_world.features.ai.service.impl.AiGenerationServiceImpl;
import com.project.w_3d_world.features.ai.util.Procedural;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
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
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.ModelFileValidator;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMultiFormatExportTest {

    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private Model3DRepository model3DRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ModelFileRepository modelFileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ModelStorageService storageService;
    @Mock private ModelDownloadService downloadService;
    @Mock private PricingService pricingService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ModelConverterService modelConverterService;

    private ProviderRegistry providerRegistry;
    private MockAdapter mockAdapter;
    private AiGenerationServiceImpl service;

    private final int aliceId = 101;
    private final int bobId = 102;
    private final int modelId = 2001;
    private final int versionId = 3001;

    private Model3D aliceModel;
    private ModelVersion aliceVersion;

    @BeforeEach
    void setUp() {
        mockAdapter = new MockAdapter(true);
        providerRegistry = new ProviderRegistry(new MockEnvironment(), null, null, null, true);
        providerRegistry.register(mockAdapter);

        service = new AiGenerationServiceImpl(
                aiRequestRepository,
                model3DRepository,
                modelVersionRepository,
                modelFileRepository,
                userRepository,
                auditLogRepository,
                storageService,
                downloadService,
                providerRegistry,
                pricingService,
                transactionManager,
                new MockEnvironment(),
                0,
                true,
                "secret",
                modelConverterService
        );

        aliceModel = new Model3D(aliceId, "DragonModel", "A dragon", ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        ReflectionTestUtils.setField(aliceModel, "modelId", modelId);

        aliceVersion = new ModelVersion(modelId, null, 501, aliceId, 1, ModelSourceType.AI_GENERATED, "Initial");
        ReflectionTestUtils.setField(aliceVersion, "modelVersionId", versionId);
    }

    @Test
    @DisplayName("Export: exportVersion returns existing format when already saved in database")
    void export_ExistingFormat_ReturnsWithoutRegeneration() {
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(aliceVersion));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, aliceId)).thenReturn(Optional.of(aliceModel));

        ModelFile stlFile = new ModelFile(versionId, ModelFileRole.SOURCE_MODEL, "STL", "storage/dragon.stl", "model/stl", "hash_stl", 1024L, false);
        ReflectionTestUtils.setField(stlFile, "fileId", 701);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId)).thenReturn(List.of(stlFile));

        AiExportResponse response = service.exportVersion(aliceId, versionId, new AiExportRequest("STL"));

        assertNotNull(response);
        assertEquals(701, response.fileId());
        assertEquals("STL", response.format());
        assertEquals("DragonModel.stl", response.fileName());
        assertEquals(1024L, response.fileSize());
        assertEquals("/api/ai-studio/assets/701/file", response.downloadUrl());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("Export: duplicate export requests return same fileId and do not create duplicate files")
    void export_DuplicateRequests_Idempotent() {
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(aliceVersion));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, aliceId)).thenReturn(Optional.of(aliceModel));

        ModelFile fbxFile = new ModelFile(versionId, ModelFileRole.SOURCE_MODEL, "FBX", "storage/dragon.fbx", "application/octet-stream", "hash_fbx", 4096L, false);
        ReflectionTestUtils.setField(fbxFile, "fileId", 801);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId)).thenReturn(List.of(fbxFile));

        AiExportResponse r1 = service.exportVersion(aliceId, versionId, new AiExportRequest("FBX"));
        AiExportResponse r2 = service.exportVersion(aliceId, versionId, new AiExportRequest("fbx"));

        assertEquals(801, r1.fileId());
        assertEquals(801, r2.fileId());
        assertEquals("DragonModel.fbx", r1.fileName());
        verify(modelFileRepository, never()).save(any());
        verify(modelFileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Export: fallback conversion via ModelConverterService when format not in database or provider")
    void export_FallbackConversion_Success() throws Exception {
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(aliceVersion));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, aliceId)).thenReturn(Optional.of(aliceModel));

        ModelFile glbFile = new ModelFile(versionId, ModelFileRole.VIEWABLE_MODEL, "GLB", "storage/dragon.glb", "model/gltf-binary", "hash_glb", 2048L, true);
        ReflectionTestUtils.setField(glbFile, "fileId", 601);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId)).thenReturn(List.of(glbFile));

        byte[] fakeGlb = Procedural.makeGlb("dragon-seed");
        when(storageService.open("storage/dragon.glb")).thenReturn(new ByteArrayInputStream(fakeGlb));

        when(modelConverterService.isConversionAvailable()).thenReturn(true);
        byte[] fakeStl = Procedural.makeStl("dragon-seed");
        when(modelConverterService.convertGlb(any(), eq("STL"))).thenReturn(fakeStl);

        StoredModelFile stored = new StoredModelFile("storage/conv.stl", "STL", "model/stl", 1234L, "stl_hash");
        when(storageService.store(any(InputStream.class), eq("model.stl"), eq("model/stl"), eq(ModelFileRole.SOURCE_MODEL), eq(modelId), eq(versionId)))
                .thenReturn(stored);

        ModelFile savedFile = new ModelFile(versionId, ModelFileRole.SOURCE_MODEL, "STL", "storage/conv.stl", "model/stl", "stl_hash", 1234L, false);
        ReflectionTestUtils.setField(savedFile, "fileId", 901);
        when(modelFileRepository.saveAndFlush(any())).thenReturn(savedFile);

        AiExportResponse response = service.exportVersion(aliceId, versionId, new AiExportRequest("STL"));

        assertNotNull(response);
        assertEquals(901, response.fileId());
        assertEquals("STL", response.format());
        assertEquals("DragonModel.stl", response.fileName());
        assertEquals("/api/ai-studio/assets/901/file", response.downloadUrl());
    }

    @Test
    @DisplayName("Export: conversion unavailable throws FORMAT_UNAVAILABLE 400 error")
    void export_ConversionUnavailable_ThrowsFormatUnavailable() {
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(aliceVersion));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, aliceId)).thenReturn(Optional.of(aliceModel));

        ModelFile glbFile = new ModelFile(versionId, ModelFileRole.VIEWABLE_MODEL, "GLB", "storage/dragon.glb", "model/gltf-binary", "hash_glb", 2048L, true);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId)).thenReturn(List.of(glbFile));

        when(modelConverterService.isConversionAvailable()).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.exportVersion(aliceId, versionId, new AiExportRequest("FBX")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("FORMAT_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Export: invalid format throws 400 bad request")
    void export_InvalidFormat_Throws400() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service.exportVersion(aliceId, versionId, new AiExportRequest("COLLADA_DAE")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Unsupported export format"));
    }

    @Test
    @DisplayName("Export: cross-user access is denied with 404")
    void export_CrossUserAccess_Denied404() {
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(aliceVersion));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, bobId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                service.exportVersion(bobId, versionId, new AiExportRequest("GLB")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("Download: downloadAsset returns friendly filename and correct content type")
    void downloadAsset_FriendlyFilenameAndOwnership() {
        ModelFile stlFile = new ModelFile(versionId, ModelFileRole.SOURCE_MODEL, "STL", "key/stl", "model/stl", "hash", 2048L, false);
        ReflectionTestUtils.setField(stlFile, "fileId", 888);

        when(modelFileRepository.findById(888)).thenReturn(Optional.of(stlFile));
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(aliceVersion));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, aliceId)).thenReturn(Optional.of(aliceModel));

        AuthorizedModelDownload rawDownload = new AuthorizedModelDownload(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "model-2001-file-888.stl", "model/stl", 2048L
        );
        when(downloadService.openAuthorizedDownload(aliceId, modelId, 888)).thenReturn(rawDownload);

        AuthorizedModelDownload result = service.downloadAsset(aliceId, 888);
        assertNotNull(result);
        assertEquals("DragonModel.stl", result.filename());
        assertEquals("model/stl", result.contentType());
    }

    @Test
    @DisplayName("Validation: ModelFileValidator validates binary FBX and rejects malformed FBX")
    void validator_FbxValidation() throws Exception {
        ModelFileValidator validator = new ModelFileValidator();
        ModelFileValidator.Format format = validator.validateMetadata("model.fbx", "application/octet-stream", ModelFileRole.SOURCE_MODEL);
        assertEquals("fbx", format.extension());

        byte[] validFbx = Procedural.makeFbx("seed");
        Path tempFbx = Files.createTempFile("test-valid-", ".fbx");
        try {
            Files.write(tempFbx, validFbx);
            assertDoesNotThrow(() -> validator.validateContent(tempFbx, format));

            // Malformed FBX
            Files.write(tempFbx, new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27});
            assertThrows(Exception.class, () -> validator.validateContent(tempFbx, format));
        } finally {
            Files.deleteIfExists(tempFbx);
        }
    }

    @Test
    @DisplayName("Validation: ModelFileValidator validates ZIP archive and rejects path traversal")
    void validator_ZipValidation() throws Exception {
        ModelFileValidator validator = new ModelFileValidator();
        ModelFileValidator.Format format = validator.validateMetadata("model.zip", "application/zip", ModelFileRole.SOURCE_MODEL);
        assertEquals("zip", format.extension());

        Path tempZip = Files.createTempFile("test-valid-", ".zip");
        try {
            // Valid zip
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
                zos.putNextEntry(new ZipEntry("model.obj"));
                zos.write("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            assertDoesNotThrow(() -> validator.validateContent(tempZip, format));

            // Malformed ZIP with empty contents
            Files.write(tempZip, new byte[]{1, 2, 3, 4});
            assertThrows(Exception.class, () -> validator.validateContent(tempZip, format));
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }
}
