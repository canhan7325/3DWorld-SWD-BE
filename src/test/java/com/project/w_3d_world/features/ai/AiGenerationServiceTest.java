package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.AiEstimateRequest;
import com.project.w_3d_world.features.ai.dto.AiEstimateResponse;
import com.project.w_3d_world.features.ai.dto.AiTaskDetailResponse;
import com.project.w_3d_world.features.ai.dto.GenerateTextRequest;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.MockAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.ai.service.PricingService;
import com.project.w_3d_world.features.ai.util.Procedural;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGenerationServiceTest {

    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private Model3DRepository model3DRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private ModelFileRepository modelFileRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ModelStorageService storageService;

    private ProviderRegistry providerRegistry;
    private PricingService pricingService;
    private AiGenerationService service;

    private User testUser;
    private int userId = 101;

    @BeforeEach
    void setUp() throws Exception {
        providerRegistry = new ProviderRegistry("", "", "", true); // Mock provider
        pricingService = new PricingService();
        service = new AiGenerationService(
                aiRequestRepository,
                model3DRepository,
                modelVersionRepository,
                modelFileRepository,
                userRepository,
                auditLogRepository,
                storageService,
                providerRegistry,
                pricingService,
                0 // 0 credits for baseline test
        );

        testUser = new User("test@example.com", "hash", "Test User", "0900000000");
        setField(testUser, "id", userId);
        setField(testUser, "aiCreditsBalance", 100);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("generateText should create model, ai_request, version, and file adhering to 48-table constraints")
    void generateText_Success() throws Exception {
        when(userRepository.lockById(userId)).thenReturn(Optional.of(testUser));

        Model3D savedModel = new Model3D(userId, "Castle", "A medieval castle",
                ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(savedModel, "modelId", 2001);
        when(model3DRepository.saveAndFlush(any(Model3D.class))).thenReturn(savedModel);
        when(model3DRepository.findById(2001)).thenReturn(Optional.of(savedModel));

        AiRequest savedAiReq = new AiRequest(userId, 2001, null, "TEXT_TO_3D", "GENERATE",
                "A medieval castle", null, "MOCK", "mock-job-1", 0, "SUCCESS");
        setField(savedAiReq, "aiRequestId", 5001);
        when(aiRequestRepository.saveAndFlush(any(AiRequest.class))).thenReturn(savedAiReq);
        when(aiRequestRepository.findByAiRequestIdAndUserId(5001, userId)).thenReturn(Optional.of(savedAiReq));

        when(modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(2001)).thenReturn(Optional.empty());

        ModelVersion savedVersion = new ModelVersion(2001, null, 5001, userId, 1,
                ModelSourceType.AI_GENERATED, "Generated by MOCK");
        setField(savedVersion, "modelVersionId", 3001);
        when(modelVersionRepository.saveAndFlush(any(ModelVersion.class))).thenReturn(savedVersion);
        when(modelVersionRepository.findByModelIdOrderByVersionNumberDesc(2001)).thenReturn(List.of(savedVersion));

        StoredModelFile stored = new StoredModelFile(
                "models/2001/versions/3001/files/abcd-1234.glb", "glb", "model/gltf-binary", 1024L, "dummyhash");
        when(storageService.store(any(InputStream.class), eq("model.glb"), eq("model/gltf-binary"),
                eq(ModelFileRole.VIEWABLE_MODEL), eq(2001), eq(3001))).thenReturn(stored);

        when(storageService.open(anyString())).thenReturn(new ByteArrayInputStream(Procedural.makeGlb("test")));

        ModelFile savedFile = new ModelFile(3001, ModelFileRole.VIEWABLE_MODEL, "GLB",
                stored.storageKey(), stored.mimeType(), stored.fileHash(), stored.fileSize(), true);
        setField(savedFile, "fileId", 4001);
        when(modelFileRepository.saveAndFlush(any(ModelFile.class))).thenReturn(savedFile);
        when(modelFileRepository.findPrimaryByModelVersionId(3001)).thenReturn(Optional.of(savedFile));

        GenerateTextRequest req = new GenerateTextRequest("A medieval castle", "Castle", Map.of());
        AiTaskDetailResponse resp = service.generateText(userId, req);

        assertNotNull(resp);
        assertEquals(5001, resp.aiRequestId());
        assertEquals(2001, resp.modelId());
        assertEquals("SUCCESS", resp.status());
        assertEquals(1, resp.versions().size());
        assertEquals(1, resp.versions().get(0).versionNumber());
        assertEquals("AI_GENERATED", resp.versions().get(0).sourceType());

        verify(model3DRepository).saveAndFlush(argThat(m ->
                m.getModelType() == ModelType.AI_GENERATOR && m.getOriginType() == ModelOriginType.AI));
        verify(modelVersionRepository).saveAndFlush(argThat(v ->
                v.getSourceType() == ModelSourceType.AI_GENERATED && v.getAiRequestId() != null));
        verify(modelFileRepository).saveAndFlush(argThat(f ->
                f.getFileRole() == ModelFileRole.VIEWABLE_MODEL && "glb".equalsIgnoreCase(f.getFileFormat())));
        verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("generateImage should validate image bytes and dimension bounds")
    void generateImage_InvalidDimensions_ThrowsBadRequest() {
        // Create 10x10 tiny PNG
        byte[] tinyPng = new byte[] {
                (byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, 'I', 'H', 'D', 'R',
                0, 0, 0, 10, // width 10
                0, 0, 0, 10, // height 10
                8, 6, 0, 0, 0, 0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", tinyPng);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.generateImage(userId, "Tiny", "test", file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("Credit deduction should subtract balance and refund if provider fails")
    void generateText_CreditDeductionAndRefund() throws Exception {
        // Mock registry where task stays PENDING (not instant success)
        ProviderRegistry nonInstantRegistry = new ProviderRegistry("", "", "", true);
        nonInstantRegistry.register(new MockAdapter(false));

        // Service with 30 credits per generation
        AiGenerationService chargingService = new AiGenerationService(
                aiRequestRepository, model3DRepository, modelVersionRepository,
                modelFileRepository, userRepository, auditLogRepository,
                storageService, nonInstantRegistry, pricingService, 30
        );

        when(userRepository.lockById(userId)).thenReturn(Optional.of(testUser));

        Model3D savedModel = new Model3D(userId, "Item", "desc",
                ModelType.AI_GENERATOR, ModelOriginType.AI, "PRIVATE");
        setField(savedModel, "modelId", 2002);
        when(model3DRepository.saveAndFlush(any(Model3D.class))).thenReturn(savedModel);

        AiRequest savedAiReq = new AiRequest(userId, 2002, null, "TEXT_TO_3D", "GENERATE",
                "prompt", null, "MOCK", "mock-text-1234", 30, "PENDING");
        setField(savedAiReq, "aiRequestId", 5002);
        when(aiRequestRepository.saveAndFlush(any(AiRequest.class))).thenReturn(savedAiReq);
        when(aiRequestRepository.findByAiRequestIdAndUserId(5002, userId)).thenReturn(Optional.of(savedAiReq));

        // Initial balance: 100 - 30 = 70
        chargingService.generateText(userId, new GenerateTextRequest("prompt", "Item", Map.of()));
        assertEquals(70, testUser.getAiCreditsBalance());

        // Now cancel task: should refund 30 credits back -> 100
        when(aiRequestRepository.lockByIdAndUserId(5002, userId)).thenReturn(Optional.of(savedAiReq));
        chargingService.cancel(userId, 5002);
        assertEquals(100, testUser.getAiCreditsBalance());
        assertEquals("FAILED", savedAiReq.getStatus());
    }

    @Test
    @DisplayName("Pricing and Estimate should return normalized costs")
    void estimate_ShouldReturnCalculatedCost() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        AiEstimateRequest req = new AiEstimateRequest(Map.of("aiModel", "latest", "textureResolution", "2k"), 1, "single");
        AiEstimateResponse resp = service.estimate(userId, req);

        assertNotNull(resp);
        assertEquals(30, resp.total());
        assertEquals(100, resp.userBalance());
        assertTrue(resp.sufficient());
    }
}
