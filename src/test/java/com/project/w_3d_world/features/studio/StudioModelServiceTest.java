package com.project.w_3d_world.features.studio;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
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
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.studio.dto.request.CreateStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.request.SaveStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectSummaryResponse;
import com.project.w_3d_world.features.studio.service.StudioModelService;
import com.project.w_3d_world.features.studio.service.impl.StudioModelServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudioModelServiceTest {

    @Mock private Model3DRepository model3DRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ModelFileRepository modelFileRepository;
    @Mock private ModelStorageService storageService;
    @Mock private ModelDownloadService downloadService;
    @Mock private AuditLogRepository auditLogRepository;

    private StudioModelService service;
    private final int userId = 101;

    @BeforeEach
    void setUp() {
        service = new StudioModelServiceImpl(
                model3DRepository,
                modelVersionRepository,
                modelFileRepository,
                storageService,
                downloadService,
                auditLogRepository
        );
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("createProject should save model, initial version 1, and scene file adhering to 48-table constraints")
    void createProject_Success() throws Exception {
        Model3D savedModel = new Model3D(userId, "Vase Design", "Studio project: Vase Design",
                ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(savedModel, "modelId", 1001);
        when(model3DRepository.saveAndFlush(any(Model3D.class))).thenReturn(savedModel);
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(1001, userId))
                .thenReturn(Optional.of(savedModel));

        ModelVersion savedVersion = new ModelVersion(1001, null, null, userId, 1,
                ModelSourceType.EDITOR_SAVE, "Initial Studio scene");
        setField(savedVersion, "modelVersionId", 2001);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(savedVersion);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(1001)).thenReturn(List.of(savedVersion));

        StoredModelFile stored = new StoredModelFile(
                "models/1001/versions/2001/files/abcd-scene.json", "json", "application/json", 128L, "hash");
        when(storageService.store(any(InputStream.class), eq("scene.json"), eq("application/json"),
                eq(ModelFileRole.EDITABLE_SCENE), eq(1001), eq(2001))).thenReturn(stored);

        ModelFile savedFile = new ModelFile(2001, ModelFileRole.EDITABLE_SCENE, "JSON",
                stored.storageKey(), stored.mimeType(), stored.fileHash(), stored.fileSize(), true);
        setField(savedFile, "fileId", 3001);
        when(modelFileRepository.saveAndFlush(any(ModelFile.class))).thenReturn(savedFile);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(2001)).thenReturn(List.of(savedFile));

        when(storageService.open(stored.storageKey()))
                .thenReturn(new ByteArrayInputStream("{\"nodes\":[{\"name\":\"Vase\"}]}".getBytes(StandardCharsets.UTF_8)));

        CreateStudioProjectRequest request = new CreateStudioProjectRequest("Vase Design", Map.of("nodes", List.of()));
        StudioProjectDetailResponse resp = service.createProject(userId, request);

        assertNotNull(resp);
        assertEquals(1001, resp.modelId());
        assertEquals("Vase Design", resp.name());
        assertEquals(1, resp.currentVersion());
        assertEquals("WEB_EDITOR", resp.modelType());
        assertEquals("EDITOR", resp.originType());

        verify(model3DRepository).saveAndFlush(argThat(m ->
                m.getModelType() == ModelType.WEB_EDITOR && m.getOriginType() == ModelOriginType.EDITOR));
        verify(modelVersionRepository).saveAndFlush(argThat(v ->
                v.getSourceType() == ModelSourceType.EDITOR_SAVE && v.getAiRequestId() == null && v.getVersionNumber() == 1));
        verify(modelFileRepository).saveAndFlush(argThat(f ->
                f.getFileRole() == ModelFileRole.EDITABLE_SCENE && "json".equalsIgnoreCase(f.getFileFormat())));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("saveProject with matching expectedVersion should increment version to v2 with parent_version_id")
    void saveProject_MatchingVersion_SavesNewVersion() throws Exception {
        int modelId = 1002;
        Model3D model = new Model3D(userId, "Project 2", "desc",
                ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(model));

        ModelVersion v1 = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.EDITOR_SAVE, "Initial");
        setField(v1, "modelVersionId", 2001);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(v1));

        ModelVersion v2 = new ModelVersion(modelId, 2001, null, userId, 2, ModelSourceType.EDITOR_SAVE, "Added handles");
        setField(v2, "modelVersionId", 2002);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(v2);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v2, v1));

        StoredModelFile stored = new StoredModelFile(
                "models/1002/versions/2002/files/abcd-scene2.json", "json", "application/json", 256L, "hash2");
        when(storageService.store(any(InputStream.class), eq("scene.json"), eq("application/json"),
                eq(ModelFileRole.EDITABLE_SCENE), eq(modelId), eq(2002))).thenReturn(stored);

        ModelFile f2 = new ModelFile(2002, ModelFileRole.EDITABLE_SCENE, "JSON",
                stored.storageKey(), stored.mimeType(), stored.fileHash(), stored.fileSize(), true);
        setField(f2, "fileId", 3002);
        when(modelFileRepository.saveAndFlush(any(ModelFile.class))).thenReturn(f2);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(2002)).thenReturn(List.of(f2));

        when(storageService.open(stored.storageKey()))
                .thenReturn(new ByteArrayInputStream("{\"nodes\":[{\"name\":\"Updated\"}]}".getBytes(StandardCharsets.UTF_8)));

        SaveStudioProjectRequest request = new SaveStudioProjectRequest(1, "Added handles", Map.of("nodes", List.of()));
        StudioProjectDetailResponse resp = service.saveProject(userId, modelId, request);

        assertNotNull(resp);
        assertEquals(2, resp.currentVersion());
        verify(modelVersionRepository).saveAndFlush(argThat(v ->
                v.getVersionNumber() == 2 && Integer.valueOf(2001).equals(v.getParentVersionId())
                        && v.getSourceType() == ModelSourceType.EDITOR_SAVE && v.getAiRequestId() == null));
    }

    @Test
    @DisplayName("saveProject with mismatched expectedVersion should throw 409 Conflict (REVISION_CONFLICT)")
    void saveProject_MismatchedVersion_ThrowsConflict() throws Exception {
        int modelId = 1003;
        Model3D model = new Model3D(userId, "Project 3", "desc",
                ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));

        ModelVersion currentLatest = new ModelVersion(modelId, null, null, userId, 2, ModelSourceType.EDITOR_SAVE, "v2");
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(currentLatest));

        // Client sent expectedVersion = 1, but server is already at version 2
        SaveStudioProjectRequest request = new SaveStudioProjectRequest(1, "Outdated edit", Map.of());

        ApiException ex = assertThrows(ApiException.class, () ->
                service.saveProject(userId, modelId, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("REVISION_CONFLICT"));
        verify(modelVersionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Opening an AI-generated model in Studio and saving v2 should create valid EDITOR_SAVE version")
    void openAiModel_SaveInStudio_Succeeds() throws Exception {
        int modelId = 1004;
        // Model was originally created by AI
        Model3D aiModel = new Model3D(userId, "AI Dragon", "Dragon prompt",
                ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(aiModel, "modelId", modelId);

        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(aiModel));
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(aiModel));

        // Version 1 was AI_GENERATED
        ModelVersion v1Ai = new ModelVersion(modelId, null, 999, userId, 1, ModelSourceType.AI_GENERATED, "Generated by AI");
        setField(v1Ai, "modelVersionId", 2001);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(v1Ai));

        // Version 2 saved from Studio
        ModelVersion v2Studio = new ModelVersion(modelId, 2001, null, userId, 2, ModelSourceType.EDITOR_SAVE, "Polished in Studio");
        setField(v2Studio, "modelVersionId", 2002);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(v2Studio);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v2Studio, v1Ai));

        StoredModelFile stored = new StoredModelFile(
                "models/1004/versions/2002/files/scene-v2.json", "json", "application/json", 512L, "hash");
        when(storageService.store(any(InputStream.class), eq("scene.json"), eq("application/json"),
                eq(ModelFileRole.EDITABLE_SCENE), eq(modelId), eq(2002))).thenReturn(stored);

        ModelFile f2 = new ModelFile(2002, ModelFileRole.EDITABLE_SCENE, "JSON",
                stored.storageKey(), stored.mimeType(), stored.fileHash(), stored.fileSize(), true);
        when(modelFileRepository.saveAndFlush(any(ModelFile.class))).thenReturn(f2);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(2002)).thenReturn(List.of(f2));

        when(storageService.open(stored.storageKey()))
                .thenReturn(new ByteArrayInputStream("{\"nodes\":[{\"name\":\"AI Dragon Mod\"}]}".getBytes(StandardCharsets.UTF_8)));

        SaveStudioProjectRequest request = new SaveStudioProjectRequest(1, "Polished in Studio", Map.of("nodes", List.of()));
        StudioProjectDetailResponse resp = service.saveProject(userId, modelId, request);

        assertNotNull(resp);
        assertEquals(2, resp.currentVersion());
        // Verify v2 has parentVersionId pointing to v1, source_type is EDITOR_SAVE, aiRequestId is null
        verify(modelVersionRepository).saveAndFlush(argThat(v ->
                v.getVersionNumber() == 2
                        && Integer.valueOf(2001).equals(v.getParentVersionId())
                        && v.getSourceType() == ModelSourceType.EDITOR_SAVE
                        && v.getAiRequestId() == null));
    }
}
