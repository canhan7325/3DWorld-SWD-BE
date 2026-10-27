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
import com.project.w_3d_world.features.studio.dto.request.*;
import com.project.w_3d_world.features.studio.dto.response.*;
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
class StudioProjectMutationTest {

    @Mock private Model3DRepository model3DRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ModelFileRepository modelFileRepository;
    @Mock private ModelStorageService storageService;
    @Mock private ModelDownloadService downloadService;
    @Mock private AuditLogRepository auditLogRepository;

    private StudioModelService service;
    private final int userId = 101;
    private final int modelId = 5001;

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
    @DisplayName("PUT /content with matching baseRevision should successfully save revision + 1")
    void saveProjectContent_Success() throws Exception {
        Model3D model = new Model3D(userId, "Scene 1", "desc", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(model));

        ModelVersion v1 = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.EDITOR_SAVE, "Initial");
        setField(v1, "modelVersionId", 6001);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(v1));

        ModelVersion v2 = new ModelVersion(modelId, 6001, null, userId, 2, ModelSourceType.EDITOR_SAVE, "Added node");
        setField(v2, "modelVersionId", 6002);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(v2);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v2, v1));

        StoredModelFile stored = new StoredModelFile("models/5001/v2/scene.json", "json", "application/json", 256L, "hash2");
        when(storageService.store(any(), eq("scene.json"), eq("application/json"), eq(ModelFileRole.EDITABLE_SCENE), eq(modelId), eq(6002)))
                .thenReturn(stored);

        ModelFile file = new ModelFile(6002, ModelFileRole.EDITABLE_SCENE, "JSON", stored.storageKey(), "application/json", "hash2", 256L, true);
        setField(file, "fileId", 7002);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(6002)).thenReturn(List.of(file));
        when(storageService.open(stored.storageKey())).thenReturn(new ByteArrayInputStream("{\"nodes\":[{\"name\":\"Sphere\"}]}".getBytes(StandardCharsets.UTF_8)));

        SaveStudioContentRequest req = new SaveStudioContentRequest(1, Map.of("nodes", List.of(Map.of("name", "Sphere"))), "Added node");
        StudioProjectDetailResponse resp = service.saveProjectContent(userId, modelId, req);

        assertNotNull(resp);
        assertEquals(2, resp.currentVersion());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("PUT /content with baseRevision mismatch must throw 409 REVISION_CONFLICT")
    void saveProjectContent_ConflictOnStaleRevision() throws Exception {
        Model3D model = new Model3D(userId, "Scene 1", "desc", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));

        // Server is at revision 3
        ModelVersion v3 = new ModelVersion(modelId, null, null, userId, 3, ModelSourceType.EDITOR_SAVE, "Rev 3");
        setField(v3, "modelVersionId", 6003);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(v3));

        // Client sent baseRevision = 2
        SaveStudioContentRequest req = new SaveStudioContentRequest(2, Map.of("nodes", List.of()), "Conflicting change");

        ApiException ex = assertThrows(ApiException.class, () -> service.saveProjectContent(userId, modelId, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("REVISION_CONFLICT"));
        verify(modelVersionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PATCH /projects/{id} should update project metadata")
    void updateProject_MetadataUpdated() throws Exception {
        Model3D model = new Model3D(userId, "Old Name", "Old Desc", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(model));

        ModelVersion v1 = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.EDITOR_SAVE, "Initial");
        setField(v1, "modelVersionId", 6001);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v1));

        UpdateStudioProjectRequest req = new UpdateStudioProjectRequest("New Name", "New Description");
        StudioProjectDetailResponse resp = service.updateProject(userId, modelId, req);

        assertNotNull(resp);
        assertEquals("New Name", model.getModelName());
        assertEquals("New Description", model.getDescription());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("DELETE /projects/{id} should soft-delete the project")
    void deleteProject_SoftDeleted() throws Exception {
        Model3D model = new Model3D(userId, "Project to Delete", "desc", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));

        service.deleteProject(userId, modelId);

        assertNotNull(model.getDeletedAt());
        verify(model3DRepository).saveAndFlush(model);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("POST /duplicate should create a deep copy with a new project and version 1")
    void duplicateProject_Success() throws Exception {
        Model3D original = new Model3D(userId, "Original Model", "desc", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(original, "modelId", modelId);
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(original));

        ModelVersion origVer = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.EDITOR_SAVE, "Initial");
        setField(origVer, "modelVersionId", 6001);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(origVer));

        ModelFile origFile = new ModelFile(6001, ModelFileRole.EDITABLE_SCENE, "JSON", "storage/orig.json", "application/json", "h", 100L, true);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(6001)).thenReturn(List.of(origFile));
        when(storageService.open("storage/orig.json")).thenReturn(new ByteArrayInputStream("{\"nodes\":[{\"name\":\"Cube\"}]}".getBytes(StandardCharsets.UTF_8)));

        int newModelId = 5002;
        Model3D duplicated = new Model3D(userId, "Copy of Original Model", "Duplicated from project #5001", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(duplicated, "modelId", newModelId);
        when(model3DRepository.saveAndFlush(any(Model3D.class))).thenReturn(duplicated);
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(newModelId, userId)).thenReturn(Optional.of(duplicated));

        ModelVersion newVer = new ModelVersion(newModelId, null, null, userId, 1, ModelSourceType.EDITOR_SAVE, "Duplicated initial revision");
        setField(newVer, "modelVersionId", 6002);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(newVer);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(newModelId)).thenReturn(List.of(newVer));

        StoredModelFile stored = new StoredModelFile("models/5002/v1/scene.json", "json", "application/json", 100L, "h");
        when(storageService.store(any(), eq("scene.json"), eq("application/json"), eq(ModelFileRole.EDITABLE_SCENE), eq(newModelId), eq(6002)))
                .thenReturn(stored);

        StudioProjectDetailResponse resp = service.duplicateProject(userId, modelId);
        assertNotNull(resp);
        assertEquals(newModelId, resp.modelId());
        assertEquals("Copy of Original Model", resp.name());
    }

    @Test
    @DisplayName("Snapshots: listSnapshots, createSnapshot, and updateSnapshot")
    void snapshots_Lifecycle() throws Exception {
        Model3D model = new Model3D(userId, "Snapshot Test", "desc", ModelType.WEB_EDITOR, ModelOriginType.EDITOR, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(model));
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)).thenReturn(Optional.of(model));

        ModelVersion v1 = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.EDITOR_SAVE, "Initial");
        setField(v1, "modelVersionId", 6001);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(v1));
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v1));

        ModelFile v1File = new ModelFile(6001, ModelFileRole.EDITABLE_SCENE, "JSON", "storage/v1.json", "application/json", "h", 100L, true);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(6001)).thenReturn(List.of(v1File));

        // 1. List snapshots
        List<StudioSnapshotResponse> list = service.listSnapshots(userId, modelId);
        assertEquals(1, list.size());
        assertEquals("SNAP-5001-v1", list.get(0).snapshotCode());

        // 2. Create snapshot v2
        ModelVersion v2 = new ModelVersion(modelId, 6001, null, userId, 2, ModelSourceType.EDITOR_SAVE, "Snapshot export v2");
        setField(v2, "modelVersionId", 6002);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(v2);

        StoredModelFile stored = new StoredModelFile("models/5001/v2/snapshot.json", "json", "application/json", 100L, "h2");
        when(storageService.store(any(), eq("snapshot.json"), eq("application/json"), eq(ModelFileRole.VIEWABLE_MODEL), eq(modelId), eq(6002)))
                .thenReturn(stored);

        ModelFile v2File = new ModelFile(6002, ModelFileRole.VIEWABLE_MODEL, "JSON", stored.storageKey(), "application/json", "h2", 100L, true);
        setField(v2File, "fileId", 7002);
        when(modelFileRepository.saveAndFlush(any(ModelFile.class))).thenReturn(v2File);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(6002)).thenReturn(List.of(v2File));

        StudioSnapshotResponse created = service.createSnapshot(userId, modelId, new CreateStudioSnapshotRequest("Production freeze", "JSON"));
        assertNotNull(created);
        assertEquals("SNAP-5001-v2", created.snapshotCode());

        // 3. Update snapshot note
        when(modelVersionRepository.findByModelIdAndVersionNumber(modelId, 2)).thenReturn(Optional.of(v2));
        StudioSnapshotResponse updated = service.updateSnapshot(userId, modelId, "SNAP-5001-v2", new UpdateStudioSnapshotRequest("Updated Freeze Note"));
        assertEquals("Updated Freeze Note", updated.changeNote());
    }
}
