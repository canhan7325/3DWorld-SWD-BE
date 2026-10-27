package com.project.w_3d_world.features.ai;

import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.ai.controller.AiGenerationController;
import com.project.w_3d_world.features.ai.controller.AiWebhookController;
import com.project.w_3d_world.features.ai.dto.request.*;
import com.project.w_3d_world.features.ai.dto.response.*;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.*;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.ai.service.impl.AiGenerationServiceImpl;
import com.project.w_3d_world.features.ai.service.PricingService;
import com.project.w_3d_world.features.ai.service.impl.PricingServiceImpl;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.auth.service.impl.AuthSessionServiceImpl;
import com.project.w_3d_world.features.model.entity.*;
import com.project.w_3d_world.features.model.enums.*;
import com.project.w_3d_world.features.model.repository.*;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.service.impl.LocalModelStorageService;
import com.project.w_3d_world.features.model.service.impl.ModelDownloadServiceImpl;
import com.project.w_3d_world.features.studio.controller.StudioModelController;
import com.project.w_3d_world.features.studio.dto.request.*;
import com.project.w_3d_world.features.studio.dto.response.*;
import com.project.w_3d_world.features.studio.service.StudioModelService;
import com.project.w_3d_world.features.studio.service.impl.StudioModelServiceImpl;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(classes = AiStudioPostgresIntegrationTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AiStudioPostgresIntegrationTest {

    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:5432/ai_studio_it";
    private static final String WEBHOOK_SECRET = "test-meshy-webhook-secret-123456";
    private static Path tempStorageRoot;

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiGenerationService aiService;
    @Autowired private StudioModelService studioService;
    @Autowired private UserRepository userRepository;
    @Autowired private AiRequestRepository aiRequestRepository;
    @Autowired private Model3DRepository model3DRepository;
    @Autowired private ModelVersionRepository modelVersionRepository;
    @Autowired private ModelFileRepository modelFileRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;
    @Autowired private ProviderRegistry providerRegistry;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private Environment environment;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry properties) throws Exception {
        tempStorageRoot = Files.createTempDirectory("ai-studio-test-storage");
        properties.add("spring.datasource.url", () -> DB_URL);
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "false");
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("app.jwt.secret-key", () -> Base64.getEncoder().encodeToString("01234567890123456789012345678901234567890123456789".getBytes()));
        properties.add("app.jwt.expiration", () -> "3600000");
        properties.add("app.ai.credits-per-generation", () -> "30");
        properties.add("app.ai.demo-topup-enabled", () -> "true");
        properties.add("app.ai.webhook-secret", () -> WEBHOOK_SECRET);
        properties.add("MODEL_STORAGE_ROOT", () -> tempStorageRoot.toString());
    }

    @BeforeAll
    void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        providerRegistry.register(new MockAdapter(true));
    }

    @AfterAll
    void cleanupStorage() {
        if (tempStorageRoot != null) {
            try (var stream = Files.walk(tempStorageRoot)) {
                stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (Exception ignored) {}
        }
    }

    private User createTestUser(String email, int initialCredits) {
        String uniqueEmail = UUID.randomUUID().toString().substring(0, 8) + "_" + email;
        int userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, email_verified, phone_verified, account_status, ai_credits_balance, created_at) " +
                        "VALUES (?, '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Test User', '0901234567', true, true, 'ACTIVE', ?, now()) RETURNING user_id",
                Integer.class, uniqueEmail, initialCredits
        );

        Integer customerRoleId = jdbc.queryForObject("SELECT role_id FROM roles WHERE role_code = 'CUSTOMER'", Integer.class);
        jdbc.update("INSERT INTO user_roles (user_id, role_id, approval_status, created_at) VALUES (?, ?, 'ACTIVE', now())",
                userId, customerRoleId);

        return userRepository.findById(userId).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getId(), Set.of("CUSTOMER"), user.getPhone(), user.getEmail(), user.getAuthVersion()).getTokenValue();
    }

    // --- TEST 1: Schema 48 Tables & Hibernate Validation ---
    @Test
    @DisplayName("Gate 1: Verify PostgreSQL database has exactly 48 tables and matches ddl-auto=validate")
    void testGate1_DatabaseSchemaIs48Tables() {
        Integer tableCount = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                Integer.class
        );
        assertEquals(48, tableCount, "PostgreSQL schema must contain exactly 48 tables!");
    }

    // --- TEST 2: Complete Flow: Auth -> AI Gen -> Library -> Studio -> Version 2 -> Download ---
    @Test
    @DisplayName("Gate 2: Full End-to-End flow: Login -> Text-to-3D -> GLB -> Studio Open -> Version 2 -> Download")
    void testGate2_FullEndToEndFlow() throws Exception {
        User user = createTestUser("fullflow@test.com", 100);
        String token = bearer(user);

        // 1. Text-to-3D Task Creation
        String createJson = mapper.writeValueAsString(new GenerateTextRequest("prompt", "Castle", Map.of()));
        String respContent = mvc.perform(post("/api/ai/text-to-3d")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();

        int modelId = mapper.readTree(respContent).path("data").path("modelId").asInt();
        int requestId = mapper.readTree(respContent).path("data").path("aiRequestId").asInt();
        assertTrue(modelId > 0);
        assertTrue(requestId > 0);

        // Verify credit deduction in PostgreSQL
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(70, reloaded.getAiCreditsBalance(), "100 - 30 = 70 credits expected!");

        // Verify DB persistence in 48-table schema
        AiRequest aiReq = aiRequestRepository.findById(requestId).orElseThrow();
        assertEquals("SUCCESS", aiReq.getStatus());
        assertEquals("TEXT_TO_3D", aiReq.getRequestType());

        Model3D model = model3DRepository.findById(modelId).orElseThrow();
        assertEquals(ModelOriginType.AI, model.getOriginType());
        assertEquals(ModelType.AI_GENERATOR, model.getModelType());

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getVersionNumber());
        assertEquals(ModelSourceType.AI_GENERATED, versions.get(0).getSourceType());

        ModelFile primaryFile = modelFileRepository.findPrimaryByModelVersionId(versions.get(0).getModelVersionId()).orElseThrow();
        assertEquals(ModelFileRole.VIEWABLE_MODEL, primaryFile.getFileRole());
        assertEquals("glb", primaryFile.getFileFormat().toLowerCase());

        // 2. Open in 3D Studio
        String studioProjectJson = mvc.perform(get("/api/studio/projects/" + modelId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelId").value(modelId))
                .andExpect(jsonPath("$.data.scene").exists())
                .andReturn().getResponse().getContentAsString();

        // 3. Save new revision in 3D Studio (baseRevision = 1) -> creates Version 2
        Map<String, Object> newScene = Map.of("nodes", List.of(Map.of("name", "Tower", "type", "mesh")));
        SaveStudioContentRequest saveReq = new SaveStudioContentRequest(1, newScene, "Added tower in Studio");
        mvc.perform(put("/api/studio/projects/" + modelId + "/content")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(saveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2));

        // Verify Version 2 in database
        List<ModelVersion> updatedVersions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        assertEquals(2, updatedVersions.size());
        assertEquals(2, updatedVersions.get(0).getVersionNumber());
        assertEquals(ModelSourceType.EDITOR_SAVE, updatedVersions.get(0).getSourceType());
        assertEquals("Added tower in Studio", updatedVersions.get(0).getChangeNote());

        // Verify Version 1 is intact
        assertEquals(1, updatedVersions.get(1).getVersionNumber());
        assertEquals(ModelSourceType.AI_GENERATED, updatedVersions.get(1).getSourceType());

        // 4. Download file from Version 2
        ModelFile v2File = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(updatedVersions.get(0).getModelVersionId()).get(0);
        mvc.perform(get("/api/studio/projects/" + modelId + "/files/" + v2File.getFileId() + "/download")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    // --- TEST 3: Unauthorized & Cross-User Security ---
    @Test
    @DisplayName("Gate 3: Cross-user isolation: User B cannot access User A's tasks or studio projects")
    void testGate3_CrossUserIsolation() throws Exception {
        User userA = createTestUser("usera@test.com", 100);
        User userB = createTestUser("userb@test.com", 100);
        String tokenA = bearer(userA);
        String tokenB = bearer(userB);

        // User A creates a task and project
        AiTaskDetailResponse taskA = aiService.generateText(userA.getId(), new GenerateTextRequest("Sword", "Sword", Map.of()));
        int modelAId = taskA.modelId();
        int taskAId = taskA.aiRequestId();

        // 1. Unauthenticated request -> 401
        mvc.perform(get("/api/ai/tasks/" + taskAId))
                .andExpect(status().isUnauthorized());

        // 2. User B tries to view User A's task -> 404
        mvc.perform(get("/api/ai/tasks/" + taskAId).header("Authorization", tokenB))
                .andExpect(status().isNotFound());

        // 3. User B tries to cancel User A's task -> 404
        mvc.perform(post("/api/ai/tasks/" + taskAId + "/cancel").header("Authorization", tokenB))
                .andExpect(status().isNotFound());

        // 4. User B tries to open User A's Studio project -> 404
        mvc.perform(get("/api/studio/projects/" + modelAId).header("Authorization", tokenB))
                .andExpect(status().isNotFound());

        // 5. User B tries to save User A's Studio project -> 404
        SaveStudioContentRequest saveReq = new SaveStudioContentRequest(1, Map.of(), "Hacked");
        mvc.perform(put("/api/studio/projects/" + modelAId + "/content")
                        .header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(saveReq)))
                .andExpect(status().isNotFound());
    }

    // --- TEST 4: Credit Concurrency & Zero Overspend ---
    @Test
    @DisplayName("Gate 4: Concurrent credit deduction with 50 credits and 4 threads (cost 30) allows exactly 1 success")
    void testGate4_ConcurrentCreditDeductions() throws Exception {
        User user = createTestUser("concurrency@test.com", 50); // only 50 credits, cost is 30
        int numThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger paymentRequiredFailures = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    aiService.generateText(user.getId(), new GenerateTextRequest("Concurrent " + idx, "Prompt", Map.of()));
                    successes.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getStatus().value() == 402) {
                        paymentRequiredFailures.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly 1 concurrent request should succeed when balance is 50 and cost is 30!");
        assertEquals(3, paymentRequiredFailures.get(), "Remaining 3 concurrent requests must fail with 402 Payment Required!");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(20, reloaded.getAiCreditsBalance(), "Final balance must be exactly 20 (50 - 30)!");
        assertTrue(reloaded.getAiCreditsBalance() >= 0, "PostgreSQL ck_user_ai_credits constraint respected!");
    }

    // --- TEST 5: Cancellation & Anti-Double Refund ---
    @Test
    @DisplayName("Gate 5: Cancellation refunds credits once, duplicate cancel or cancel on completed task never double refunds")
    void testGate5_CancellationAndAntiDoubleRefund() {
        User user = createTestUser("refundtest@test.com", 100);

        // Create task with non-instant provider to keep it in PROCESSING
        ProviderRegistry nonInstantRegistry = new ProviderRegistry("", "", "", true);
        nonInstantRegistry.register(new MockAdapter(false));
        AiGenerationService service = new AiGenerationServiceImpl(
                aiRequestRepository, model3DRepository, modelVersionRepository,
                modelFileRepository, userRepository, auditLogRepository,
                new LocalModelStorageService(tempStorageRoot),
                new ModelDownloadServiceImpl(model3DRepository, modelVersionRepository, modelFileRepository, new LocalModelStorageService(tempStorageRoot)),
                nonInstantRegistry, new PricingServiceImpl(), txManager, environment, 30, true, null
        );

        AiTaskDetailResponse task = service.generateText(user.getId(), new GenerateTextRequest("Pending", "Prompt", Map.of()));
        assertEquals(70, userRepository.findById(user.getId()).orElseThrow().getAiCreditsBalance());

        // First cancel -> refunds 30 credits back to 100
        service.cancel(user.getId(), task.aiRequestId());
        assertEquals(100, userRepository.findById(user.getId()).orElseThrow().getAiCreditsBalance());
        assertEquals("FAILED", aiRequestRepository.findById(task.aiRequestId()).orElseThrow().getStatus());

        // Duplicate cancel -> does NOT refund again
        service.cancel(user.getId(), task.aiRequestId());
        assertEquals(100, userRepository.findById(user.getId()).orElseThrow().getAiCreditsBalance(), "Duplicate cancel must not refund again!");
    }

    // --- TEST 6: Studio Revision Conflicts (Optimistic Concurrency) ---
    @Test
    @DisplayName("Gate 6: Studio concurrent saves with identical baseRevision produce 1 success and 1 409 Conflict")
    void testGate6_StudioRevisionConflict() throws Exception {
        User user = createTestUser("studioconflict@test.com", 100);
        StudioProjectDetailResponse project = studioService.createProject(user.getId(), new CreateStudioProjectRequest("Test Studio", Map.of()));
        int modelId = project.modelId();
        assertEquals(1, project.currentVersion());

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int rev = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    studioService.saveProjectContent(user.getId(), modelId,
                            new SaveStudioContentRequest(1, Map.of("node", "Node" + rev), "Revision " + rev));
                    successes.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getStatus().value() == 409) {
                        conflicts.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one concurrent save must succeed!");
        assertEquals(1, conflicts.get(), "The concurrent save with outdated baseRevision must get 409 Conflict!");

        StudioProjectDetailResponse updated = studioService.getProject(user.getId(), modelId);
        assertEquals(2, updated.currentVersion(), "Current version should have advanced to 2!");
    }

    // --- TEST 7: Image Upload Contract & Ownership ---
    @Test
    @DisplayName("Gate 7: Image uploads assign UUID uploadId, list user's uploads, and reject cross-user downloads")
    void testGate7_UploadContractAndOwnership() throws Exception {
        User userA = createTestUser("uploader_a@test.com", 100);
        User userB = createTestUser("uploader_b@test.com", 100);
        String tokenA = bearer(userA);
        String tokenB = bearer(userB);

        // Create 100x100 dummy PNG with valid PNG signature and IHDR chunk
        byte[] pngBytes = new byte[512];
        pngBytes[0] = (byte)0x89; pngBytes[1] = 'P'; pngBytes[2] = 'N'; pngBytes[3] = 'G';
        pngBytes[4] = 0x0d; pngBytes[5] = 0x0a; pngBytes[6] = 0x1a; pngBytes[7] = 0x0a;
        pngBytes[8] = 0; pngBytes[9] = 0; pngBytes[10] = 0; pngBytes[11] = 13; // IHDR length
        pngBytes[12] = 'I'; pngBytes[13] = 'H'; pngBytes[14] = 'D'; pngBytes[15] = 'R';
        pngBytes[16] = 0; pngBytes[17] = 0; pngBytes[18] = 0; pngBytes[19] = 100; // width 100
        pngBytes[20] = 0; pngBytes[21] = 0; pngBytes[22] = 0; pngBytes[23] = 100; // height 100
        pngBytes[24] = 8; pngBytes[25] = 6; pngBytes[26] = 0; pngBytes[27] = 0; pngBytes[28] = 0;
        MockMultipartFile file = new MockMultipartFile("file", "test_asset.png", "image/png", pngBytes);

        // 1. User A uploads
        AiUploadResponse uploadResp = aiService.uploadSourceImage(userA.getId(), file);
        assertNotNull(uploadResp.uploadId());
        assertTrue(uploadResp.uploadId().length() > 20, "uploadId should be a UUID");

        // 2. User A lists uploads -> contains the upload
        List<AiUploadResponse> userAUploads = aiService.listUploads(userA.getId());
        assertTrue(userAUploads.stream().anyMatch(u -> u.uploadId().equals(uploadResp.uploadId())));

        // 3. User A downloads upload file -> 200 OK
        mvc.perform(get("/api/ai-studio/uploads/" + uploadResp.uploadId() + "/file")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk());

        // 4. User B attempts to download User A's upload file -> 403 Forbidden
        mvc.perform(get("/api/ai-studio/uploads/" + uploadResp.uploadId() + "/file")
                        .header("Authorization", tokenB))
                .andExpect(status().isForbidden());
    }

    // --- TEST 8: Webhook Fail-Closed Security ---
    @Test
    @DisplayName("Gate 8: Webhook rejects unauthenticated or invalid secret callbacks (fail closed)")
    void testGate8_WebhookFailClosedSecurity() throws Exception {
        // 1. Webhook with no secret header -> 401 Unauthorized
        mvc.perform(post("/api/webhooks/meshy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"meshy-task-12345\"}"))
                .andExpect(status().isUnauthorized());

        // 2. Webhook with wrong secret header -> 401 Unauthorized
        mvc.perform(post("/api/webhooks/meshy")
                        .header("X-Webhook-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"meshy-task-12345\"}"))
                .andExpect(status().isUnauthorized());

        // 3. Webhook with valid secret header -> 200 OK
        mvc.perform(post("/api/webhooks/meshy")
                        .header("X-Webhook-Secret", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"unknown-meshy-task\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.matched").value(false));
    }

    // --- TEST 9: Provider Timeout Outcome Uncertainty ---
    @Test
    @DisplayName("Gate 9: Provider network timeout leaves task in PROCESSING awaiting reconciliation without premature refund")
    void testGate9_ProviderTimeoutUncertainty() {
        User user = createTestUser("timeoutuser@test.com", 100);

        // Throwing adapter simulating network timeout
        ProviderAdapter timingOutAdapter = new ProviderAdapter() {
            @Override public String name() { return "MOCK"; }
            @Override public String submitText(String prompt, Map<String, Object> config) {
                throw new ProviderException("PROVIDER_TIMEOUT", "Connection to Meshy timed out after HTTP dispatch", 504, true);
            }
            @Override public String submitImage(byte[] imageBytes, String mimeType, Map<String, Object> config) {
                throw new ProviderException("PROVIDER_TIMEOUT", "Connection timed out", 504, true);
            }
            @Override public ProviderTask getTask(String taskId, String requestType) { return null; }
            @Override public byte[] downloadGlb(String glbUrlOrReference) { return new byte[0]; }
            @Override public void cancel(String taskId, String requestType) {}
        };

        ProviderRegistry timeoutRegistry = new ProviderRegistry("", "", "", true);
        timeoutRegistry.register(timingOutAdapter);

        AiGenerationService timeoutService = new AiGenerationServiceImpl(
                aiRequestRepository, model3DRepository, modelVersionRepository,
                modelFileRepository, userRepository, auditLogRepository,
                new LocalModelStorageService(tempStorageRoot),
                new ModelDownloadServiceImpl(model3DRepository, modelVersionRepository, modelFileRepository, new LocalModelStorageService(tempStorageRoot)),
                timeoutRegistry, new PricingServiceImpl(), txManager, environment, 30, true, null
        );

        AiTaskDetailResponse response = timeoutService.generateText(user.getId(), new GenerateTextRequest("Timeout Prompt", "Timeout Model", Map.of()));
        assertNotNull(response);

        // Status must be PROCESSING (under 48-table CHECK constraint) with failureReason noting timeout awaiting reconciliation
        assertEquals("PROCESSING", response.status());
        assertTrue(response.failureReason().startsWith("PROVIDER_TIMEOUT_AWAITING_RECONCILIATION"));

        // Credits must NOT have been refunded prematurely
        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(70, reloadedUser.getAiCreditsBalance(), "Credits must NOT be refunded while provider outcome is uncertain!");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @EntityScan(basePackageClasses = {User.class, com.project.w_3d_world.features.auth.entity.UserRole.class, com.project.w_3d_world.features.auth.entity.Role.class, AiRequest.class, Model3D.class, ModelVersion.class, ModelFile.class, AuditLog.class})
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, AiRequestRepository.class, Model3DRepository.class,
            ModelVersionRepository.class, ModelFileRepository.class, AuditLogRepository.class})
    @Import({
            SecurityConfig.class,
            GlobalExceptionHandler.class,
            AiGenerationController.class,
            StudioModelController.class,
            AiWebhookController.class,
            AiGenerationServiceImpl.class,
            StudioModelServiceImpl.class,
            ModelDownloadServiceImpl.class,
            PricingServiceImpl.class,
            JwtService.class,
            AuthSessionServiceImpl.class
    })
    static class Config {
        @Bean
        public ProviderRegistry providerRegistry() {
            return new ProviderRegistry("", "", "", true);
        }

        @Bean
        public ModelStorageService modelStorageService(Environment environment) {
            return new LocalModelStorageService(Path.of(environment.getProperty("MODEL_STORAGE_ROOT")));
        }
    }
}
