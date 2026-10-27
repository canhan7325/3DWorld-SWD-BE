package com.project.w_3d_world.features.ai.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.*;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.ProviderAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.provider.ProviderTask;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.util.GlbStats;
import com.project.w_3d_world.features.ai.util.ImageProbe;
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
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class AiGenerationService {
    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20 MB
    private static final int MIN_IMAGE_SIDE = 64;
    private static final int MAX_IMAGE_SIDE = 8192;

    private final AiRequestRepository aiRequestRepository;
    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ModelStorageService storageService;
    private final ModelDownloadService downloadService;
    private final ProviderRegistry providerRegistry;
    private final PricingService pricingService;
    private final TransactionTemplate txTemplate;
    private final Environment environment;
    private final int defaultCreditsPerGeneration;
    private final boolean demoTopupEnabled;
    private final String webhookSecret;

    public AiGenerationService(
            AiRequestRepository aiRequestRepository,
            Model3DRepository model3DRepository,
            ModelVersionRepository modelVersionRepository,
            ModelFileRepository modelFileRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            ModelStorageService storageService,
            ModelDownloadService downloadService,
            ProviderRegistry providerRegistry,
            PricingService pricingService,
            PlatformTransactionManager transactionManager,
            Environment environment,
            @Value("${app.ai.credits-per-generation:0}") int defaultCreditsPerGeneration,
            @Value("${app.ai.demo-topup-enabled:true}") boolean demoTopupEnabled,
            @Value("${app.ai.webhook-secret:}") String webhookSecret
    ) {
        this.aiRequestRepository = aiRequestRepository;
        this.model3DRepository = model3DRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.modelFileRepository = modelFileRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageService = storageService;
        this.downloadService = downloadService;
        this.providerRegistry = providerRegistry;
        this.pricingService = pricingService;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.defaultCreditsPerGeneration = Math.max(0, defaultCreditsPerGeneration);
        this.demoTopupEnabled = demoTopupEnabled;
        this.webhookSecret = webhookSecret;
    }

    public AiGenerationService(
            AiRequestRepository aiRequestRepository,
            Model3DRepository model3DRepository,
            ModelVersionRepository modelVersionRepository,
            ModelFileRepository modelFileRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            ModelStorageService storageService,
            ProviderRegistry providerRegistry,
            PricingService pricingService,
            int defaultCreditsPerGeneration
    ) {
        this(
                aiRequestRepository,
                model3DRepository,
                modelVersionRepository,
                modelFileRepository,
                userRepository,
                auditLogRepository,
                storageService,
                null,
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
                defaultCreditsPerGeneration,
                true,
                null
        );
    }

    private static String checkedName(String name, String fallback) {
        String val = (name == null || name.isBlank()) ? fallback : name.trim();
        if (val.length() > 255) {
            val = val.substring(0, 255);
        }
        return val;
    }

    private record InitialAiRequestData(AiRequest aiRequest, Model3D model, int creditCost) {}

    public AiTaskDetailResponse generateText(int userId, GenerateTextRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Prompt cannot be blank.");
        }
        String prompt = request.prompt().trim();
        if (prompt.length() > 800) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Prompt cannot exceed 800 characters.");
        }

        Map<String, Object> normConfig = pricingService.normalizeConfig(request.config(), "single");
        Map<String, Object> est = pricingService.estimate(normConfig, 1);
        int creditCost = defaultCreditsPerGeneration > 0 ? defaultCreditsPerGeneration : ((Number) est.get("total")).intValue();
        String modelName = checkedName(request.name(), prompt.length() > 40 ? prompt.substring(0, 40) + "..." : prompt);

        ProviderAdapter provider = providerRegistry.get();

        // Phase 1: Short DB transaction to deduct credits and create PENDING records
        InitialAiRequestData initData = txTemplate.execute(status -> {
            User user = userRepository.lockById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
            if (!user.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active.");
            }

            if (creditCost > 0) {
                user.deductAiCredits(creditCost);
                userRepository.saveAndFlush(user);
            }

            Model3D model = model3DRepository.saveAndFlush(new Model3D(
                    userId,
                    modelName,
                    prompt,
                    ModelType.AI_GENERATOR,
                    ModelOriginType.AI,
                    "PRIVATE"
            ));

            AiRequest aiRequest = aiRequestRepository.saveAndFlush(new AiRequest(
                    userId,
                    model.getModelId(),
                    null,
                    "TEXT_TO_3D",
                    "GENERATE",
                    prompt,
                    null,
                    provider.name(),
                    null,
                    creditCost,
                    "PENDING"
            ));

            return new InitialAiRequestData(aiRequest, model, creditCost);
        });

        // Phase 2: Outside DB transaction - call AI provider HTTP API
        String providerJobId = null;
        Exception providerError = null;
        try {
            providerJobId = provider.submitText(prompt, normConfig);
        } catch (Exception e) {
            providerError = e;
        }

        // Phase 3: Short DB transaction to update request status or handle error & refund
        if (providerJobId != null) {
            final String jobId = providerJobId;
            txTemplate.executeWithoutResult(status -> {
                AiRequest aiReq = initData.aiRequest();
                aiReq.markProcessing(jobId);
                aiRequestRepository.saveAndFlush(aiReq);

                // If provider completed immediately (e.g. MockAdapter), complete now
                try {
                    ProviderTask task = provider.getTask(jobId, "TEXT_TO_3D");
                    if (task != null && task.isSuccess()) {
                        completeGeneration(aiReq, initData.model(), provider, task);
                    }
                } catch (Exception e) {
                    log.warn("Immediate task check failed: {}", e.getMessage());
                }
            });
        } else {
            final Exception err = providerError;
            txTemplate.executeWithoutResult(status -> {
                log.error("Failed to submit text-to-3d job to provider", err);
                AiRequest aiReq = initData.aiRequest();
                aiReq.markFailed("Provider submission failed: " + (err != null ? err.getMessage() : "Unknown error"));
                aiRequestRepository.saveAndFlush(aiReq);
                if (initData.creditCost() > 0) {
                    userRepository.lockById(userId).ifPresent(u -> {
                        u.addAiCredits(initData.creditCost());
                        userRepository.saveAndFlush(u);
                    });
                }
            });
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_TEXT_TO_3D_REQUESTED", "AI_REQUEST",
                initData.aiRequest().getAiRequestId(), "Prompt: " + (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt), null
        ));

        return detail(userId, initData.aiRequest().getAiRequestId());
    }

    public AiTaskDetailResponse generateImage(int userId, String name, String prompt, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file is required.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image exceeds maximum allowed size of 20 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read image bytes.");
        }

        String mime = ImageProbe.sniffMime(bytes);
        if (mime == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid image format. Supported formats: JPEG, PNG, WEBP.");
        }

        ImageProbe.Dim dim;
        try {
            dim = ImageProbe.dimensions(bytes, mime);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corrupt or unreadable image file.");
        }

        if (Math.min(dim.width(), dim.height()) < MIN_IMAGE_SIDE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image too small. Minimum dimension is " + MIN_IMAGE_SIDE + "px.");
        }
        if (Math.max(dim.width(), dim.height()) > MAX_IMAGE_SIDE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image too large. Maximum dimension is " + MAX_IMAGE_SIDE + "px.");
        }

        Map<String, Object> normConfig = pricingService.normalizeConfig(null, "single");
        Map<String, Object> est = pricingService.estimate(normConfig, 1);
        int creditCost = defaultCreditsPerGeneration > 0 ? defaultCreditsPerGeneration : ((Number) est.get("total")).intValue();

        String originalName = file.getOriginalFilename() == null ? "image." + mime.substring(6) : file.getOriginalFilename();
        String modelName = checkedName(name, originalName);

        // Store input image into local storage
        String ext = mime.contains("png") ? "png" : (mime.contains("webp") ? "webp" : "jpg");
        String inputStorageKey = "ai/inputs/" + userId + "/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + "." + ext;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storageService.store(in, "input." + ext, mime, ModelFileRole.SOURCE_MODEL, 0, 0);
        } catch (Exception e) {
            log.warn("Storage store with key: {}", inputStorageKey);
        }

        ProviderAdapter provider = providerRegistry.get();

        // Phase 1: Short DB transaction to deduct credits and persist initial records
        InitialAiRequestData initData = txTemplate.execute(status -> {
            User user = userRepository.lockById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
            if (!user.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active.");
            }

            if (creditCost > 0) {
                user.deductAiCredits(creditCost);
                userRepository.saveAndFlush(user);
            }

            Model3D model = model3DRepository.saveAndFlush(new Model3D(
                    userId,
                    modelName,
                    prompt == null ? "Image-to-3D: " + originalName : prompt,
                    ModelType.AI_GENERATOR,
                    ModelOriginType.AI,
                    "PRIVATE"
            ));

            AiRequest aiRequest = aiRequestRepository.saveAndFlush(new AiRequest(
                    userId,
                    model.getModelId(),
                    null,
                    "IMAGE_TO_3D",
                    "GENERATE",
                    prompt,
                    inputStorageKey,
                    provider.name(),
                    null,
                    creditCost,
                    "PENDING"
            ));

            return new InitialAiRequestData(aiRequest, model, creditCost);
        });

        // Phase 2: Outside DB transaction - call AI provider HTTP API
        String providerJobId = null;
        Exception providerError = null;
        try {
            providerJobId = provider.submitImage(bytes, mime, normConfig);
        } catch (Exception e) {
            providerError = e;
        }

        // Phase 3: Short DB transaction to update request status or handle error & refund
        if (providerJobId != null) {
            final String jobId = providerJobId;
            txTemplate.executeWithoutResult(status -> {
                AiRequest aiReq = initData.aiRequest();
                aiReq.markProcessing(jobId);
                aiRequestRepository.saveAndFlush(aiReq);

                try {
                    ProviderTask task = provider.getTask(jobId, "IMAGE_TO_3D");
                    if (task != null && task.isSuccess()) {
                        completeGeneration(aiReq, initData.model(), provider, task);
                    }
                } catch (Exception e) {
                    log.warn("Immediate task check failed: {}", e.getMessage());
                }
            });
        } else {
            final Exception err = providerError;
            txTemplate.executeWithoutResult(status -> {
                log.error("Failed to submit image-to-3d job to provider", err);
                AiRequest aiReq = initData.aiRequest();
                aiReq.markFailed("Provider submission failed: " + (err != null ? err.getMessage() : "Unknown error"));
                aiRequestRepository.saveAndFlush(aiReq);
                if (initData.creditCost() > 0) {
                    userRepository.lockById(userId).ifPresent(u -> {
                        u.addAiCredits(initData.creditCost());
                        userRepository.saveAndFlush(u);
                    });
                }
            });
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_IMAGE_TO_3D_REQUESTED", "AI_REQUEST",
                initData.aiRequest().getAiRequestId(), "File: " + originalName, null
        ));

        return detail(userId, initData.aiRequest().getAiRequestId());
    }

    public AiTaskDetailResponse createUnifiedTask(int userId, CreateAiUnifiedTaskRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        String mode = request.mode() != null ? request.mode().trim().toUpperCase(Locale.ROOT) : "TEXT_TO_3D";
        if ("IMAGE_TO_3D".equalsIgnoreCase(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image-to-3D requires an image file upload. Please use multipart/form-data.");
        }
        GenerateTextRequest textReq = new GenerateTextRequest(request.name(), request.prompt(), request.config());
        return generateText(userId, textReq);
    }

    @Transactional
    public AiTaskDetailResponse refresh(int userId, int requestId) {
        AiRequest request = aiRequestRepository.lockByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI request not found."));

        if ("SUCCESS".equals(request.getStatus()) || "FAILED".equals(request.getStatus())) {
            return detail(userId, requestId);
        }

        String externalId = request.getProviderJobReference();
        if (externalId == null || externalId.isBlank()) {
            return detail(userId, requestId);
        }

        ProviderAdapter provider = providerRegistry.get(request.getProviderName());
        ProviderTask task;
        try {
            task = provider.getTask(externalId, request.getRequestType());
        } catch (Exception e) {
            log.warn("Failed to check provider task status for request {}: {}", requestId, e.getMessage());
            return detail(userId, requestId);
        }

        Model3D model = model3DRepository.findById(request.getModelId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Associated 3D model not found."));

        if (task.isSuccess()) {
            completeGeneration(request, model, provider, task);
        } else if ("FAILED".equals(task.status()) || "CANCELED".equals(task.status())) {
            request.markFailed(task.errorMessage() != null ? task.errorMessage() : "Provider generation " + task.status());
            aiRequestRepository.saveAndFlush(request);
            if (request.getCreditsUsed() > 0) {
                userRepository.lockById(userId).ifPresent(u -> {
                    u.addAiCredits(request.getCreditsUsed());
                    userRepository.saveAndFlush(u);
                });
            }
            auditLogRepository.save(new AuditLog(
                    userId, "AI_REQUEST_FAILED", "AI_REQUEST",
                    requestId, "Reason: " + request.getFailureReason(), null
            ));
        }

        return detail(userId, requestId);
    }

    @Transactional
    public AiTaskDetailResponse cancel(int userId, int requestId) {
        AiRequest request = aiRequestRepository.lockByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI request not found."));

        if ("SUCCESS".equals(request.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot cancel already completed generation task.");
        }
        if ("FAILED".equals(request.getStatus())) {
            return detail(userId, requestId);
        }

        ProviderAdapter provider = providerRegistry.get(request.getProviderName());
        if (request.getProviderJobReference() != null) {
            try {
                provider.cancel(request.getProviderJobReference(), request.getRequestType());
            } catch (Exception e) {
                log.warn("Provider cancellation returned error: {}", e.getMessage());
            }
        }

        request.markFailed("Cancelled by user");
        aiRequestRepository.saveAndFlush(request);

        if (request.getCreditsUsed() > 0) {
            userRepository.lockById(userId).ifPresent(u -> {
                u.addAiCredits(request.getCreditsUsed());
                userRepository.saveAndFlush(u);
            });
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_REQUEST_CANCELLED", "AI_REQUEST",
                requestId, "User initiated cancellation", null
        ));

        return detail(userId, requestId);
    }

    private void completeGeneration(AiRequest request, Model3D model, ProviderAdapter provider, ProviderTask task) {
        // Idempotency: if a ModelVersion for this ai_request_id already exists, skip duplicate creation
        if (request.getAiRequestId() != null && modelVersionRepository.findByAiRequestId(request.getAiRequestId()).isPresent()) {
            log.info("Model version for AI request #{} already exists; skipping duplicate generation", request.getAiRequestId());
            request.markSuccess();
            aiRequestRepository.saveAndFlush(request);
            return;
        }

        byte[] glbBytes = provider.downloadGlb(task.glbUrl());
        if (glbBytes == null || glbBytes.length == 0) {
            request.markFailed("Downloaded GLB model is empty.");
            aiRequestRepository.saveAndFlush(request);
            return;
        }

        int nextVersion = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(model.getModelId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        ModelVersion version = modelVersionRepository.saveAndFlush(new ModelVersion(
                model.getModelId(),
                null,
                request.getAiRequestId(),
                request.getUserId(),
                nextVersion,
                ModelSourceType.AI_GENERATED,
                "Generated by " + provider.name()
        ));

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(glbBytes)) {
            stored = storageService.store(
                    in,
                    "model.glb",
                    "model/gltf-binary",
                    ModelFileRole.VIEWABLE_MODEL,
                    model.getModelId(),
                    version.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store generated GLB asset: " + e.getMessage());
        }

        modelFileRepository.saveAndFlush(new ModelFile(
                version.getModelVersionId(),
                ModelFileRole.VIEWABLE_MODEL,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        request.markSuccess();
        aiRequestRepository.saveAndFlush(request);

        auditLogRepository.save(new AuditLog(
                request.getUserId(), "AI_MODEL_GENERATED", "MODEL",
                model.getModelId(), "Generated v" + nextVersion + " from AI Request #" + request.getAiRequestId(), null
        ));
    }

    @Transactional(readOnly = true)
    public List<AiTaskDetailResponse> list(int userId) {
        List<AiRequest> requests = aiRequestRepository.findByUserIdOrderByCreatedAtDescAiRequestIdDesc(userId);
        return requests.stream().map(r -> mapDetail(userId, r)).toList();
    }

    @Transactional(readOnly = true)
    public List<AiTaskDetailResponse> listTasksFiltered(int userId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        PageRequest pageRequest = PageRequest.of(safePage, safeSize);

        Page<AiRequest> requestPage;
        if (status != null && !status.isBlank()) {
            requestPage = aiRequestRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status.trim().toUpperCase(Locale.ROOT), pageRequest);
        } else {
            requestPage = aiRequestRepository.findByUserIdOrderByCreatedAtDescAiRequestIdDesc(userId, pageRequest);
        }

        return requestPage.getContent().stream().map(r -> mapDetail(userId, r)).toList();
    }

    @Transactional(readOnly = true)
    public AiTaskDetailResponse detail(int userId, int requestId) {
        AiRequest request = aiRequestRepository.findByAiRequestIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI request not found."));
        return mapDetail(userId, request);
    }

    private AiTaskDetailResponse mapDetail(int userId, AiRequest request) {
        Model3D model = model3DRepository.findById(request.getModelId()).orElse(null);
        String modelName = model != null ? model.getModelName() : "Model #" + request.getModelId();

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(request.getModelId());
        List<AiTaskDetailResponse.VersionSummary> versionSummaries = new ArrayList<>();
        Map<String, Object> stats = null;

        for (ModelVersion v : versions) {
            ModelFile primaryFile = modelFileRepository.findPrimaryByModelVersionId(v.getModelVersionId()).orElse(null);
            Integer fileId = primaryFile != null ? primaryFile.getFileId() : null;
            String downloadUrl = fileId != null ? "/api/models/" + request.getModelId() + "/files/" + fileId + "/download" : null;

            versionSummaries.add(new AiTaskDetailResponse.VersionSummary(
                    v.getModelVersionId(),
                    v.getVersionNumber(),
                    v.getSourceType().name(),
                    v.getCreatedAt(),
                    fileId,
                    downloadUrl
            ));

            if (stats == null && primaryFile != null && "GLB".equalsIgnoreCase(primaryFile.getFileFormat())) {
                try (InputStream stream = storageService.open(primaryFile.getStorageKey())) {
                    byte[] glbBytes = stream.readAllBytes();
                    stats = GlbStats.of(glbBytes);
                } catch (Exception ignored) {}
            }
        }

        int progress = "SUCCESS".equals(request.getStatus()) ? 100 : ("PROCESSING".equals(request.getStatus()) ? 50 : 0);

        return new AiTaskDetailResponse(
                request.getAiRequestId(),
                request.getModelId(),
                modelName,
                request.getRequestType(),
                request.getActionType(),
                request.getStatus(),
                progress,
                request.getProviderName(),
                request.getProviderJobReference(),
                request.getCreditsUsed(),
                request.getFailureReason(),
                request.getCreatedAt(),
                request.getCompletedAt(),
                versionSummaries,
                stats
        );
    }

    @Transactional(readOnly = true)
    public AiConfigOptionsResponse configOptions() {
        List<Map<String, Object>> models = new ArrayList<>();
        PricingService.MODELS.forEach((k, m) -> models.add(Map.of(
                "id", k,
                "label", m.label(),
                "textureResolutions", List.copyOf(m.tex().keySet()),
                "geometryResolutions", m.geo()
        )));
        return new AiConfigOptionsResponse(
                providerRegistry.defaultProviderName(),
                PricingService.PRICE_VERSION,
                models,
                PricingService.PRESETS,
                Map.of("maxBytes", MAX_IMAGE_SIZE, "minSide", MIN_IMAGE_SIDE, "maxSide", MAX_IMAGE_SIDE)
        );
    }

    @Transactional(readOnly = true)
    public AiEstimateResponse estimate(int userId, AiEstimateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        Map<String, Object> cfg = pricingService.normalizeConfig(request == null ? null : request.config(), request == null ? "single" : request.mode());
        int variants = (request != null && request.variants() != null) ? request.variants() : 1;
        Map<String, Object> est = pricingService.estimate(cfg, variants);

        int total = ((Number) est.get("total")).intValue();
        int balance = user.getAiCreditsBalance();

        return new AiEstimateResponse(
                ((Number) est.get("perVariant")).intValue(),
                ((Number) est.get("variants")).intValue(),
                total,
                balance,
                balance >= total,
                PricingService.PRICE_VERSION,
                cfg
        );
    }

    @Transactional(readOnly = true)
    public AiWalletResponse wallet(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        List<AiRequest> tasks = aiRequestRepository.findByUserIdOrderByCreatedAtDescAiRequestIdDesc(userId);
        long successful = tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();
        return new AiWalletResponse(user.getAiCreditsBalance(), tasks.size(), successful);
    }

    @Transactional
    public AiWalletResponse topup(int userId, AiTopupRequest request) {
        if (request == null || request.amount() == null || request.amount() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be a positive integer.");
        }
        boolean isDevOrTest = environment != null && environment.acceptsProfiles(Profiles.of("test", "dev"));
        if (!demoTopupEnabled && !isDevOrTest) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Demo credit top-up is disabled in production environments.");
        }

        User user = userRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        user.addAiCredits(request.amount());
        userRepository.saveAndFlush(user);

        auditLogRepository.save(new AuditLog(
                userId, "AI_CREDITS_DEMO_TOPUP", "USER",
                userId, "Added " + request.amount() + " credits via demo topup", null
        ));

        return wallet(userId);
    }

    public AiUploadResponse uploadSourceImage(int userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file is required.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image exceeds maximum allowed size of 20 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read image bytes.");
        }

        String mime = ImageProbe.sniffMime(bytes);
        if (mime == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid image format. Supported formats: JPEG, PNG, WEBP.");
        }

        ImageProbe.Dim dim;
        try {
            dim = ImageProbe.dimensions(bytes, mime);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corrupt or unreadable image file.");
        }

        if (Math.min(dim.width(), dim.height()) < MIN_IMAGE_SIDE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image too small. Minimum dimension is " + MIN_IMAGE_SIDE + "px.");
        }
        if (Math.max(dim.width(), dim.height()) > MAX_IMAGE_SIDE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image too large. Maximum dimension is " + MAX_IMAGE_SIDE + "px.");
        }

        String originalName = file.getOriginalFilename() == null ? "image." + mime.substring(6) : file.getOriginalFilename();
        String uploadId = UUID.randomUUID().toString();
        String ext = mime.contains("png") ? "png" : (mime.contains("webp") ? "webp" : "jpg");
        String storageKey = "ai/uploads/" + userId + "/" + uploadId + "." + ext;

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storageService.store(in, uploadId + "." + ext, mime, ModelFileRole.SOURCE_MODEL, 0, 0);
        } catch (Exception e) {
            log.warn("Stored standalone image under key: {}", storageKey);
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_IMAGE_UPLOADED", "UPLOAD",
                null, "Uploaded " + originalName + " (" + file.getSize() + " bytes)", null
        ));

        String downloadUrl = "/api/ai-studio/uploads/" + uploadId + "/file";
        return new AiUploadResponse(
                uploadId,
                storageKey,
                originalName,
                file.getSize(),
                mime,
                downloadUrl,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<AiUploadResponse> listUploads(int userId) {
        List<AiRequest> requests = aiRequestRepository.findByUserIdAndInputImageStorageKeyIsNotNullOrderByCreatedAtDesc(userId);
        List<AiUploadResponse> result = new ArrayList<>();
        for (AiRequest r : requests) {
            String key = r.getInputImageStorageKey();
            String filename = key.substring(key.lastIndexOf('/') + 1);
            String mime = filename.endsWith(".png") ? "image/png" : (filename.endsWith(".webp") ? "image/webp" : "image/jpeg");
            result.add(new AiUploadResponse(
                    String.valueOf(r.getAiRequestId()),
                    key,
                    filename,
                    null,
                    mime,
                    "/api/ai-studio/uploads/" + r.getAiRequestId() + "/file",
                    r.getCreatedAt()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadUploadFile(int userId, String uploadId) {
        Integer requestId = null;
        try {
            requestId = Integer.parseInt(uploadId);
        } catch (NumberFormatException ignored) {}

        if (requestId != null) {
            AiRequest req = aiRequestRepository.findByAiRequestIdAndUserId(requestId, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Upload not found."));
            if (req.getInputImageStorageKey() == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "No image file attached to this task.");
            }
            String key = req.getInputImageStorageKey();
            InputStream is = storageService.open(key);
            String mime = key.endsWith(".png") ? "image/png" : (key.endsWith(".webp") ? "image/webp" : "image/jpeg");
            String filename = key.substring(key.lastIndexOf('/') + 1);
            return new AuthorizedModelDownload(is, filename, mime, null);
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Upload asset not found.");
    }

    @Transactional(readOnly = true)
    public List<AiProjectSummaryResponse> listProjects(int userId) {
        List<Model3D> models = model3DRepository.findAll().stream()
                .filter(m -> Objects.equals(m.getOwnerUserId(), userId)
                        && m.getDeletedAt() == null
                        && (m.getModelType() == ModelType.AI_GENERATOR || m.getOriginType() == ModelOriginType.AI))
                .sorted(Comparator.comparing(Model3D::getCreatedAt).reversed())
                .toList();

        List<AiProjectSummaryResponse> results = new ArrayList<>();
        for (Model3D m : models) {
            ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(m.getModelId()).orElse(null);
            int latestVer = latest != null ? latest.getVersionNumber() : 1;
            Integer primaryFileId = null;
            String downloadUrl = null;
            Map<String, Object> stats = null;

            if (latest != null) {
                ModelFile f = modelFileRepository.findPrimaryByModelVersionId(latest.getModelVersionId()).orElse(null);
                if (f != null) {
                    primaryFileId = f.getFileId();
                    downloadUrl = "/api/ai/assets/" + f.getFileId() + "/file";
                    if ("GLB".equalsIgnoreCase(f.getFileFormat())) {
                        try (InputStream s = storageService.open(f.getStorageKey())) {
                            stats = GlbStats.of(s.readAllBytes());
                        } catch (Exception ignored) {}
                    }
                }
            }

            results.add(new AiProjectSummaryResponse(
                    m.getModelId(),
                    m.getModelName(),
                    m.getDescription(),
                    m.getVisibility(),
                    latestVer,
                    primaryFileId,
                    downloadUrl,
                    m.getCreatedAt(),
                    m.getUpdatedAt(),
                    stats
            ));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public AiProjectDetailResponse getProject(int userId, int modelId) {
        Model3D model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI project not found."));

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        List<AiTaskDetailResponse.VersionSummary> versionSummaries = new ArrayList<>();
        Map<String, Object> stats = null;

        for (ModelVersion v : versions) {
            ModelFile pf = modelFileRepository.findPrimaryByModelVersionId(v.getModelVersionId()).orElse(null);
            Integer fileId = pf != null ? pf.getFileId() : null;
            String downloadUrl = fileId != null ? "/api/ai/assets/" + fileId + "/file" : null;
            versionSummaries.add(new AiTaskDetailResponse.VersionSummary(
                    v.getModelVersionId(),
                    v.getVersionNumber(),
                    v.getSourceType().name(),
                    v.getCreatedAt(),
                    fileId,
                    downloadUrl
            ));
            if (stats == null && pf != null && "GLB".equalsIgnoreCase(pf.getFileFormat())) {
                try (InputStream s = storageService.open(pf.getStorageKey())) {
                    stats = GlbStats.of(s.readAllBytes());
                } catch (Exception ignored) {}
            }
        }

        List<AiRequest> requests = aiRequestRepository.findByModelIdOrderByCreatedAtDesc(modelId);
        List<AiProjectDetailResponse.AiTaskSummary> taskSummaries = requests.stream().map(r ->
                new AiProjectDetailResponse.AiTaskSummary(
                        r.getAiRequestId(),
                        r.getRequestType(),
                        r.getActionType(),
                        r.getStatus(),
                        r.getPrompt(),
                        r.getProviderName(),
                        r.getCreditsUsed(),
                        r.getCreatedAt(),
                        r.getCompletedAt()
                )
        ).toList();

        return new AiProjectDetailResponse(
                model.getModelId(),
                model.getModelName(),
                model.getDescription(),
                model.getVisibility(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                versionSummaries,
                taskSummaries,
                stats
        );
    }

    @Transactional
    public AiProjectDetailResponse updateProject(int userId, int modelId, UpdateAiProjectRequest request) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI project not found."));

        if (request != null) {
            model.updateMetadata(request.name(), request.description(), request.visibility());
            model3DRepository.saveAndFlush(model);
            auditLogRepository.save(new AuditLog(
                    userId, "AI_PROJECT_UPDATED", "MODEL",
                    modelId, "Updated metadata for project " + model.getModelName(), null
            ));
        }
        return getProject(userId, modelId);
    }

    @Transactional(readOnly = true)
    public AiVersionDetailResponse getVersion(int userId, int versionId) {
        ModelVersion version = modelVersionRepository.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model version not found."));

        Model3D model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(version.getModelId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found or access denied."));

        List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId);
        Map<String, Object> stats = null;
        List<AiVersionDetailResponse.AiFileInfo> fileInfos = new ArrayList<>();

        for (ModelFile f : files) {
            fileInfos.add(new AiVersionDetailResponse.AiFileInfo(
                    f.getFileId(),
                    f.getFileRole().name(),
                    f.getFileFormat(),
                    f.getFileSize(),
                    "/api/ai/assets/" + f.getFileId() + "/file"
            ));
            if (stats == null && "GLB".equalsIgnoreCase(f.getFileFormat())) {
                try (InputStream s = storageService.open(f.getStorageKey())) {
                    stats = GlbStats.of(s.readAllBytes());
                } catch (Exception ignored) {}
            }
        }

        return new AiVersionDetailResponse(
                version.getModelVersionId(),
                version.getModelId(),
                version.getParentVersionId(),
                version.getVersionNumber(),
                version.getSourceType().name(),
                version.getChangeNote(),
                version.getCreatedAt(),
                fileInfos,
                stats
        );
    }

    @Transactional(readOnly = true)
    public AiDimensionsResponse computeDimensions(int userId, int versionId, AiDimensionsRequest request) {
        ModelVersion version = modelVersionRepository.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version not found."));
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(version.getModelId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found or access denied."));

        ModelFile glbFile = modelFileRepository.findPrimaryByModelVersionId(versionId)
                .filter(f -> "GLB".equalsIgnoreCase(f.getFileFormat()))
                .orElse(null);

        Map<String, Object> stats = null;
        if (glbFile != null) {
            try (InputStream stream = storageService.open(glbFile.getStorageKey())) {
                stats = GlbStats.of(stream.readAllBytes());
            } catch (Exception e) {
                log.warn("Failed to compute dimensions for version {}: {}", versionId, e.getMessage());
            }
        }

        Map<String, Object> computedDims = new LinkedHashMap<>();
        Map<String, Object> boundingBox = new LinkedHashMap<>();
        Map<String, Object> meshStats = new LinkedHashMap<>();

        if (stats != null) {
            Object bbox = stats.get("boundingBox");
            if (bbox instanceof Map<?, ?> bboxMap) {
                bboxMap.forEach((k, v) -> boundingBox.put(String.valueOf(k), v));
            }
            Object dims = stats.get("dimensions");
            if (dims instanceof Map<?, ?> dimMap) {
                dimMap.forEach((k, v) -> computedDims.put(String.valueOf(k), v));
            }
            meshStats.put("triangles", stats.get("triangles"));
            meshStats.put("vertices", stats.get("vertices"));
        }

        String note = "Dimensions computed directly from GLB mesh geometry. Persistent user overrides are constrained by the 48-table schema.";
        return new AiDimensionsResponse(versionId, computedDims, boundingBox, meshStats, note);
    }

    @Transactional(readOnly = true)
    public AiExportResponse exportVersion(int userId, int versionId, AiExportRequest request) {
        String format = (request != null && request.format() != null) ? request.format().trim().toUpperCase(Locale.ROOT) : "GLB";
        ModelVersion version = modelVersionRepository.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version not found."));
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(version.getModelId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found or access denied."));

        if (!"GLB".equalsIgnoreCase(format)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Format '" + format + "' is currently unsupported for export. Only native 'GLB' is supported.");
        }

        ModelFile primaryGlb = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId).stream()
                .filter(f -> "GLB".equalsIgnoreCase(f.getFileFormat()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No GLB file found for version " + versionId));

        String downloadUrl = "/api/ai/assets/" + primaryGlb.getFileId() + "/file";
        return new AiExportResponse(
                primaryGlb.getFileId(),
                primaryGlb.getFileFormat(),
                primaryGlb.getFileSize(),
                downloadUrl,
                primaryGlb.getFileHash(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadAsset(int userId, int fileId) {
        ModelFile file = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Asset file not found."));
        ModelVersion version = modelVersionRepository.findById(file.getModelVersionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model version not found."));
        Model3D model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(version.getModelId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found or access denied."));
        return downloadService.openAuthorizedDownload(userId, model.getModelId(), fileId);
    }

    public Map<String, Object> handleMeshyWebhook(Map<String, Object> payload, String secret) {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!Objects.equals(webhookSecret, secret)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret.");
            }
        }
        if (payload == null || !payload.containsKey("id")) {
            return Map.of("status", "IGNORED", "message", "No task id found in payload");
        }

        String externalTaskId = String.valueOf(payload.get("id"));
        log.info("Received Meshy webhook for task {}", externalTaskId);

        List<AiRequest> matching = aiRequestRepository.findAll().stream()
                .filter(r -> Objects.equals(r.getProviderJobReference(), externalTaskId))
                .toList();

        if (matching.isEmpty()) {
            return Map.of("status", "ACKNOWLEDGED", "matched", false);
        }

        for (AiRequest req : matching) {
            try {
                refresh(req.getUserId(), req.getAiRequestId());
            } catch (Exception e) {
                log.warn("Error refreshing task from webhook: {}", e.getMessage());
            }
        }

        return Map.of("status", "PROCESSED", "matched", true, "count", matching.size());
    }
}
