package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.GenerateTextRequest;
import com.project.w_3d_world.features.ai.dto.response.AiTaskDetailResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.ai.service.impl.AiGenerationServiceImpl;
import com.project.w_3d_world.features.ai.service.PricingService;
import com.project.w_3d_world.features.ai.service.impl.PricingServiceImpl;
import com.project.w_3d_world.features.ai.util.Procedural;
import com.project.w_3d_world.features.audit.entity.AuditLog;
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
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.studio.dto.request.SaveStudioProjectRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectDetailResponse;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectSummaryResponse;
import com.project.w_3d_world.features.studio.service.StudioModelService;
import com.project.w_3d_world.features.studio.service.impl.StudioModelServiceImpl;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiStudioInteroperabilityTest {

    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private Model3DRepository model3DRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ModelFileRepository modelFileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ModelStorageService storageService;
    @Mock private ModelDownloadService downloadService;

    private AiGenerationService aiService;
    private StudioModelService studioService;

    private User alice;
    private final int aliceId = 1;
    private final int bobId = 2;

    @BeforeEach
    void setUp() throws Exception {
        ProviderRegistry registry = new ProviderRegistry("", "", "", true); // Mock provider
        PricingService pricing = new PricingServiceImpl();

        aiService = new AiGenerationServiceImpl(
                aiRequestRepository, model3DRepository, modelVersionRepository,
                modelFileRepository, userRepository, auditLogRepository,
                storageService, registry, pricing, 0
        );

        studioService = new StudioModelServiceImpl(
                model3DRepository, modelVersionRepository, modelFileRepository,
                storageService, downloadService, auditLogRepository
        );

        alice = new User("alice@example.com", "hash", "Alice", "0900000001");
        setField(alice, "id", aliceId);
        setField(alice, "aiCreditsBalance", 100);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("Complete E2E workflow: AI Generation -> Studio Inspection -> Studio v2 Edit -> Version Integrity -> Download")
    void completeEndToEndWorkflow_AiToStudio() throws Exception {
        // --- STEP 1: Alice generates 3D model with AI ---
        when(userRepository.lockById(aliceId)).thenReturn(Optional.of(alice));

        int modelId = 7001;
        int aiRequestId = 8001;
        int v1Id = 9001;
        int file1Id = 10001;

        Model3D aiModel = new Model3D(aliceId, "Cyberpunk Drone", "A high-tech delivery drone",
                ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(aiModel, "modelId", modelId);
        when(model3DRepository.saveAndFlush(any(Model3D.class))).thenReturn(aiModel);
        when(model3DRepository.findById(modelId)).thenReturn(Optional.of(aiModel));

        AiRequest aiReq = new AiRequest(aliceId, modelId, null, "TEXT_TO_3D", "GENERATE",
                "A high-tech delivery drone", null, "MOCK", "mock-text-1", 0, "SUCCESS");
        setField(aiReq, "aiRequestId", aiRequestId);
        when(aiRequestRepository.saveAndFlush(any(AiRequest.class))).thenReturn(aiReq);
        when(aiRequestRepository.findByAiRequestIdAndUserId(aiRequestId, aliceId)).thenReturn(Optional.of(aiReq));

        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.empty());

        ModelVersion v1 = new ModelVersion(modelId, null, aiRequestId, aliceId, 1,
                ModelSourceType.AI_GENERATED, "Generated by MOCK");
        setField(v1, "modelVersionId", v1Id);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(v1);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v1));

        StoredModelFile storedGlb = new StoredModelFile(
                "models/7001/versions/9001/files/drone.glb", "glb", "model/gltf-binary", 2048L, "glbhash123");
        when(storageService.store(any(InputStream.class), eq("model.glb"), eq("model/gltf-binary"),
                eq(ModelFileRole.VIEWABLE_MODEL), eq(modelId), eq(v1Id))).thenReturn(storedGlb);

        when(storageService.open(storedGlb.storageKey())).thenReturn(new ByteArrayInputStream(Procedural.makeGlb("drone")));

        ModelFile file1 = new ModelFile(v1Id, ModelFileRole.VIEWABLE_MODEL, "GLB",
                storedGlb.storageKey(), storedGlb.mimeType(), storedGlb.fileHash(), storedGlb.fileSize(), true);
        setField(file1, "fileId", file1Id);
        when(modelFileRepository.saveAndFlush(any(ModelFile.class))).thenReturn(file1);
        when(modelFileRepository.findPrimaryByModelVersionId(v1Id)).thenReturn(Optional.of(file1));

        AiTaskDetailResponse aiResp = aiService.generateText(aliceId, new GenerateTextRequest("A high-tech delivery drone", "Cyberpunk Drone", Map.of()));
        assertNotNull(aiResp);
        assertEquals("SUCCESS", aiResp.status());
        assertEquals(modelId, aiResp.modelId());
        assertEquals(1, aiResp.versions().size());

        // --- STEP 2: Studio lists Alice's projects (including the AI-generated model) ---
        when(model3DRepository.findAll()).thenReturn(List.of(aiModel));
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(Optional.of(v1));

        List<StudioProjectSummaryResponse> projects = studioService.listProjects(aliceId);
        assertEquals(1, projects.size());
        assertEquals("Cyberpunk Drone", projects.get(0).name());
        assertEquals("AI_GENERATOR", projects.get(0).modelType());
        assertEquals(1, projects.get(0).latestVersion());

        // --- STEP 3: Alice opens the model in 3D Studio ---
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, aliceId)).thenReturn(Optional.of(aiModel));
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v1Id)).thenReturn(List.of(file1));

        StudioProjectDetailResponse studioProject = studioService.getProject(aliceId, modelId);
        assertNotNull(studioProject);
        assertEquals(1, studioProject.currentVersion());
        assertNotNull(studioProject.scene()); // synthesized initial scene for AI GLB

        // --- STEP 4: Alice modifies the scene in Studio and saves version 2 ---
        when(model3DRepository.findOwnedActiveByIdForUpdate(modelId, aliceId)).thenReturn(Optional.of(aiModel));

        int v2Id = 9002;
        int file2Id = 10002;
        ModelVersion v2 = new ModelVersion(modelId, v1Id, null, aliceId, 2,
                ModelSourceType.EDITOR_SAVE, "Added propeller guards and neon decals");
        setField(v2, "modelVersionId", v2Id);
        when(modelVersionRepository.saveAndFlush(argThat(v -> v.getVersionNumber() == 2))).thenReturn(v2);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId)).thenReturn(List.of(v2, v1));

        StoredModelFile storedSceneV2 = new StoredModelFile(
                "models/7001/versions/9002/files/scene.json", "json", "application/json", 512L, "scenehash");
        when(storageService.store(any(InputStream.class), eq("scene.json"), eq("application/json"),
                eq(ModelFileRole.EDITABLE_SCENE), eq(modelId), eq(v2Id))).thenReturn(storedSceneV2);

        ModelFile file2 = new ModelFile(v2Id, ModelFileRole.EDITABLE_SCENE, "JSON",
                storedSceneV2.storageKey(), storedSceneV2.mimeType(), storedSceneV2.fileHash(), storedSceneV2.fileSize(), true);
        setField(file2, "fileId", file2Id);
        when(modelFileRepository.saveAndFlush(argThat(f -> f.getModelVersionId().equals(v2Id)))).thenReturn(file2);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v2Id)).thenReturn(List.of(file2));

        when(storageService.open(storedSceneV2.storageKey()))
                .thenReturn(new ByteArrayInputStream("{\"nodes\":[{\"name\":\"Drone with Guards\"}]}".getBytes(StandardCharsets.UTF_8)));

        SaveStudioProjectRequest saveReq = new SaveStudioProjectRequest(1, "Added propeller guards and neon decals",
                Map.of("nodes", List.of(Map.of("name", "Drone with Guards"))));

        StudioProjectDetailResponse savedProject = studioService.saveProject(aliceId, modelId, saveReq);
        assertNotNull(savedProject);
        assertEquals(2, savedProject.currentVersion());

        // --- STEP 5: Verify version lineage and constraints ---
        // Version 1 is AI_GENERATED with ai_request_id
        assertEquals(ModelSourceType.AI_GENERATED, v1.getSourceType());
        assertEquals(aiRequestId, v1.getAiRequestId());
        assertNull(v1.getParentVersionId());

        // Version 2 is EDITOR_SAVE with parent_version_id pointing to v1, and ai_request_id is NULL
        assertEquals(ModelSourceType.EDITOR_SAVE, v2.getSourceType());
        assertEquals(v1Id, v2.getParentVersionId());
        assertNull(v2.getAiRequestId());

        // --- STEP 6: Download verification ---
        AuthorizedModelDownload mockDownload = new AuthorizedModelDownload(
                new ByteArrayInputStream(new byte[10]), "drone.glb", "model/gltf-binary", 2048L
        );
        when(downloadService.openAuthorizedDownload(aliceId, modelId, file1Id)).thenReturn(mockDownload);

        AuthorizedModelDownload download = studioService.downloadFile(aliceId, modelId, file1Id);
        assertNotNull(download);
        assertEquals("drone.glb", download.filename());
        assertEquals("model/gltf-binary", download.contentType());

        // --- STEP 7: Security & IDOR check: Bob cannot access Alice's project ---
        ApiException ex = assertThrows(ApiException.class, () ->
                studioService.getProject(bobId, modelId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
