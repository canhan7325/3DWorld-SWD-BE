package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.*;
import com.project.w_3d_world.features.ai.dto.response.*;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.MockAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.ai.service.impl.AiGenerationServiceImpl;
import com.project.w_3d_world.features.ai.service.PricingService;
import com.project.w_3d_world.features.ai.service.impl.PricingServiceImpl;
import com.project.w_3d_world.features.ai.util.Procedural;
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
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiStudioCompatibilityApiTest {

    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private Model3DRepository model3DRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ModelFileRepository modelFileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ModelStorageService storageService;
    @Mock private ModelDownloadService downloadService;

    private ProviderRegistry providerRegistry;
    private PricingService pricingService;
    private AiGenerationService service;

    private User testUser;
    private final int userId = 101;

    @BeforeEach
    void setUp() throws Exception {
        providerRegistry = new ProviderRegistry("", "", "", true);
        pricingService = new PricingServiceImpl();
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
                new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                    @Override
                    protected Object doGetTransaction() { return new Object(); }
                    @Override
                    protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
                    @Override
                    protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                    @Override
                    protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                },
                null,
                0,
                true,
                null
        );

        testUser = new User("test@example.com", "hash", "Test User", "0900000000");
        setField(testUser, "id", userId);
        testUser.addAiCredits(100);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("Unified task creation (POST /api/ai-studio/tasks) should route to text generation")
    void createUnifiedTask_TextMode() throws Exception {
        when(userRepository.lockById(userId)).thenReturn(Optional.of(testUser));

        Model3D savedModel = new Model3D(userId, "Dragon", "Fantasy dragon", ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(savedModel, "modelId", 2001);
        when(model3DRepository.saveAndFlush(any(Model3D.class))).thenReturn(savedModel);
        when(model3DRepository.findById(2001)).thenReturn(Optional.of(savedModel));

        AiRequest savedAiReq = new AiRequest(userId, 2001, null, "TEXT_TO_3D", "GENERATE", "Fantasy dragon", null, "MOCK", "mock-1", 0, "SUCCESS");
        setField(savedAiReq, "aiRequestId", 3001);
        when(aiRequestRepository.saveAndFlush(any(AiRequest.class))).thenReturn(savedAiReq);
        when(aiRequestRepository.findByAiRequestIdAndUserId(3001, userId)).thenReturn(Optional.of(savedAiReq));

        ModelVersion version = new ModelVersion(2001, null, 3001, userId, 1, ModelSourceType.AI_GENERATED, "Generated");
        setField(version, "modelVersionId", 4001);
        when(modelVersionRepository.saveAndFlush(any())).thenReturn(version);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(2001)).thenReturn(List.of(version));

        StoredModelFile stored = new StoredModelFile("m/2001/v/4001/model.glb", "glb", "model/gltf-binary", 512L, "h");
        when(storageService.store(any(), any(), any(), any(), any(), any())).thenReturn(stored);
        when(storageService.open(anyString())).thenReturn(new ByteArrayInputStream(Procedural.makeGlb("dragon")));

        ModelFile file = new ModelFile(4001, ModelFileRole.VIEWABLE_MODEL, "GLB", stored.storageKey(), stored.mimeType(), stored.fileHash(), stored.fileSize(), true);
        setField(file, "fileId", 5001);
        when(modelFileRepository.saveAndFlush(any())).thenReturn(file);
        when(modelFileRepository.findPrimaryByModelVersionId(4001)).thenReturn(Optional.of(file));

        CreateAiUnifiedTaskRequest taskReq = new CreateAiUnifiedTaskRequest("TEXT_TO_3D", "Dragon", "Fantasy dragon", Map.of(), 1);
        AiTaskDetailResponse resp = service.createUnifiedTask(userId, taskReq);

        assertNotNull(resp);
        assertEquals(3001, resp.aiRequestId());
        assertEquals("SUCCESS", resp.status());
    }

    @Test
    @DisplayName("AI Projects: listProjects, getProject, and updateProject metadata")
    void aiProjects_Management() throws Exception {
        Model3D aiModel = new Model3D(userId, "Sci-Fi Helmet", "Helmet", ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(aiModel, "modelId", 2002);
        when(model3DRepository.findAll()).thenReturn(List.of(aiModel));
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(2002, userId)).thenReturn(Optional.of(aiModel));
        when(model3DRepository.findOwnedActiveByIdForUpdate(2002, userId)).thenReturn(Optional.of(aiModel));

        ModelVersion v1 = new ModelVersion(2002, null, 3002, userId, 1, ModelSourceType.AI_GENERATED, "Gen v1");
        setField(v1, "modelVersionId", 4002);
        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(2002)).thenReturn(Optional.of(v1));
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(2002)).thenReturn(List.of(v1));

        ModelFile file = new ModelFile(4002, ModelFileRole.VIEWABLE_MODEL, "GLB", "key/model.glb", "model/gltf-binary", "h", 1024L, true);
        setField(file, "fileId", 5002);
        when(modelFileRepository.findPrimaryByModelVersionId(4002)).thenReturn(Optional.of(file));

        // 1. List
        List<AiProjectSummaryResponse> list = service.listProjects(userId);
        assertEquals(1, list.size());
        assertEquals("Sci-Fi Helmet", list.get(0).modelName());

        // 2. Detail
        AiProjectDetailResponse detail = service.getProject(userId, 2002);
        assertNotNull(detail);
        assertEquals(2002, detail.modelId());

        // 3. Update
        UpdateAiProjectRequest updateReq = new UpdateAiProjectRequest("Updated Helmet", "New description", "PUBLIC");
        AiProjectDetailResponse updated = service.updateProject(userId, 2002, updateReq);
        assertEquals("Updated Helmet", aiModel.getModelName());
        assertEquals("PUBLIC", aiModel.getVisibility());
    }

    @Test
    @DisplayName("Dimensions: computeDimensions calculates bounding box from GLB geometry")
    void computeDimensions_CalculatedFromGlb() throws Exception {
        int versionId = 4003;
        int modelId = 2003;
        ModelVersion version = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.AI_GENERATED, "Initial");
        setField(version, "modelVersionId", versionId);
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        Model3D model = new Model3D(userId, "Cube", "desc", ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(model));

        ModelFile glb = new ModelFile(versionId, ModelFileRole.VIEWABLE_MODEL, "GLB", "key/cube.glb", "model/gltf-binary", "h", 1024L, true);
        when(modelFileRepository.findPrimaryByModelVersionId(versionId)).thenReturn(Optional.of(glb));
        when(storageService.open("key/cube.glb")).thenReturn(new ByteArrayInputStream(Procedural.makeGlb("cube")));

        AiDimensionsResponse dims = service.computeDimensions(userId, versionId, new AiDimensionsRequest(null, null, null, "mm"));
        assertNotNull(dims);
        assertNotNull(dims.computedDimensions());
        assertNotNull(dims.boundingBox());
        assertTrue(dims.note().contains("48-table schema"));
    }

    @Test
    @DisplayName("Export: exportVersion returns GLB download information and rejects unsupported formats")
    void exportVersion_GlbSupported_StlRejected() throws Exception {
        int versionId = 4004;
        int modelId = 2004;
        ModelVersion version = new ModelVersion(modelId, null, null, userId, 1, ModelSourceType.AI_GENERATED, "Initial");
        setField(version, "modelVersionId", versionId);
        when(modelVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        Model3D model = new Model3D(userId, "Model", "desc", ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(model, "modelId", modelId);
        when(model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)).thenReturn(Optional.of(model));

        ModelFile glb = new ModelFile(versionId, ModelFileRole.VIEWABLE_MODEL, "GLB", "key/export.glb", "model/gltf-binary", "hash123", 2048L, true);
        setField(glb, "fileId", 5004);
        when(modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId)).thenReturn(List.of(glb));

        // GLB export succeeds
        AiExportResponse glbExport = service.exportVersion(userId, versionId, new AiExportRequest("GLB"));
        assertNotNull(glbExport);
        assertEquals(5004, glbExport.fileId());
        assertEquals("GLB", glbExport.format());

        // STL format throws 400 (no fake conversion!)
        ApiException ex = assertThrows(ApiException.class, () -> service.exportVersion(userId, versionId, new AiExportRequest("STL")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("unsupported"));
    }

    @Test
    @DisplayName("Top-up: topup credits in dev/test environment")
    void topup_DevTestAllowed() throws Exception {
        when(userRepository.lockById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(aiRequestRepository.findByUserIdOrderByCreatedAtDescAiRequestIdDesc(userId)).thenReturn(List.of());

        int initialBalance = testUser.getAiCreditsBalance();
        AiWalletResponse wallet = service.topup(userId, new AiTopupRequest(50));

        assertNotNull(wallet);
        assertEquals(initialBalance + 50, wallet.availableCredits());
        verify(userRepository).saveAndFlush(testUser);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Uploads: listUploads returns user's historical AI input images")
    void listUploads_Success() throws Exception {
        AiRequest req = new AiRequest(userId, 2001, null, "IMAGE_TO_3D", "GENERATE", "Prompt", "ai/inputs/101/input.png", "MOCK", "j-1", 30, "SUCCESS");
        setField(req, "aiRequestId", 3005);
        when(aiRequestRepository.findByUserIdAndInputImageStorageKeyIsNotNullOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(req));

        List<AiUploadResponse> uploads = service.listUploads(userId);
        assertEquals(1, uploads.size());
        assertEquals("input.png", uploads.get(0).fileName());
        assertEquals("image/png", uploads.get(0).mimeType());
    }

    private static byte[] validPngBytes() {
        byte[] b = new byte[512];
        b[0] = (byte)0x89; b[1] = 'P'; b[2] = 'N'; b[3] = 'G';
        b[4] = 0x0d; b[5] = 0x0a; b[6] = 0x1a; b[7] = 0x0a;
        b[8] = 0; b[9] = 0; b[10] = 0; b[11] = 13;
        b[12] = 'I'; b[13] = 'H'; b[14] = 'D'; b[15] = 'R';
        b[16] = 0; b[17] = 0; b[18] = 0; b[19] = 100;
        b[20] = 0; b[21] = 0; b[22] = 0; b[23] = 100;
        b[24] = 8; b[25] = 6; b[26] = 0; b[27] = 0; b[28] = 0;
        return b;
    }

    @Test
    @DisplayName("A1: Unified task with imageId, consent, and pricing check succeeds")
    void createUnifiedTask_ImageTo3D_ValidFlow() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "source.png", "image/png", validPngBytes());
        AiUploadResponse uploaded = service.uploadSourceImage(userId, file);
        assertNotNull(uploaded.uploadId());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.lockById(userId)).thenReturn(Optional.of(testUser));

        Model3D aiModel = new Model3D(userId, "Unified Model", "Prompt", ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(aiModel, "modelId", 2005);
        when(model3DRepository.saveAndFlush(any())).thenReturn(aiModel);

        AiRequest savedReq = new AiRequest(userId, 2005, null, "IMAGE_TO_3D", "GENERATE", "Prompt", "key", "MOCK", "job-1", 20, "SUCCESS");
        setField(savedReq, "aiRequestId", 3010);
        when(aiRequestRepository.saveAndFlush(any())).thenReturn(savedReq);
        when(aiRequestRepository.lockByIdAndUserId(eq(3010), eq(userId))).thenReturn(Optional.of(savedReq));
        when(aiRequestRepository.findByAiRequestIdAndUserId(3010, userId)).thenReturn(Optional.of(savedReq));
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(2005)).thenReturn(List.of());

        CreateAiUnifiedTaskRequest request = new CreateAiUnifiedTaskRequest(
                "single", "Unified Model", "Prompt", Map.of(), 1,
                List.of(uploaded.uploadId()), null, true, "pv-2026-10-22", 30
        );

        AiTaskDetailResponse resp = service.createUnifiedTask(userId, request);
        assertNotNull(resp);
        assertEquals(3010, resp.aiRequestId());
    }

    @Test
    @DisplayName("A1: Unified task requires consent (throws 400 CONSENT_REQUIRED)")
    void createUnifiedTask_ConsentRequired() {
        CreateAiUnifiedTaskRequest request = new CreateAiUnifiedTaskRequest(
                "single", "No Consent", "Prompt", Map.of(), 1,
                List.of("up-123"), null, false, "pv-2026-10-22", 20
        );
        ApiException ex = assertThrows(ApiException.class, () -> service.createUnifiedTask(userId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("A1: Unified task with unknown imageId throws 400 INVALID_IMAGES")
    void createUnifiedTask_InvalidImage() {
        CreateAiUnifiedTaskRequest request = new CreateAiUnifiedTaskRequest(
                "single", "Bad Image", "Prompt", Map.of(), 1,
                List.of("non-existent-uuid"), null, true, "pv-2026-10-22", 20
        );
        ApiException ex = assertThrows(ApiException.class, () -> service.createUnifiedTask(userId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("INVALID_IMAGES"));
    }

    @Test
    @DisplayName("A1: Unified task with mismatched quotedCredits throws 409 PRICE_CHANGED")
    void createUnifiedTask_PriceChanged() {
        MockMultipartFile file = new MockMultipartFile("file", "source.png", "image/png", validPngBytes());
        AiUploadResponse uploaded = service.uploadSourceImage(userId, file);

        CreateAiUnifiedTaskRequest request = new CreateAiUnifiedTaskRequest(
                "single", "Mismatched Price", "Prompt", Map.of(), 1,
                List.of(uploaded.uploadId()), null, true, "pv-2026-10-22", 999
        );
        ApiException ex = assertThrows(ApiException.class, () -> service.createUnifiedTask(userId, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("PRICE_CHANGED"));
    }

    @Test
    @DisplayName("A1: Unified task with insufficient credits throws 402 INSUFFICIENT_CREDITS")
    void createUnifiedTask_InsufficientCredits() {
        MockMultipartFile file = new MockMultipartFile("file", "source.png", "image/png", validPngBytes());
        AiUploadResponse uploaded = service.uploadSourceImage(userId, file);

        User poorUser = new User("poor@example.com", "hash", "0123456789", "Poor User");
        when(userRepository.findById(userId)).thenReturn(Optional.of(poorUser));

        CreateAiUnifiedTaskRequest request = new CreateAiUnifiedTaskRequest(
                "single", "No Money", "Prompt", Map.of(), 1,
                List.of(uploaded.uploadId()), null, true, "pv-2026-10-22", 30
        );
        ApiException ex = assertThrows(ApiException.class, () -> service.createUnifiedTask(userId, request));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, ex.getStatus());
        assertTrue(ex.getMessage().contains("INSUFFICIENT_CREDITS"));
    }
}
