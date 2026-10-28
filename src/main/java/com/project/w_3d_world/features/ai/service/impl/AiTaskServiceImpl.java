package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.AiEstimateRequest;
import com.project.w_3d_world.features.ai.dto.request.CreateAiUnifiedTaskRequest;
import com.project.w_3d_world.features.ai.dto.request.GenerateTextRequest;
import com.project.w_3d_world.features.ai.dto.response.AiConfigOptionsResponse;
import com.project.w_3d_world.features.ai.dto.response.AiEstimateResponse;
import com.project.w_3d_world.features.ai.dto.response.AiTaskDetailResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.model.StagedUpload;
import com.project.w_3d_world.features.ai.provider.ProviderAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.provider.ProviderTask;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiExportService;
import com.project.w_3d_world.features.ai.service.AiTaskService;
import com.project.w_3d_world.features.ai.service.AiUploadService;
import com.project.w_3d_world.features.ai.service.AiWalletService;
import com.project.w_3d_world.features.ai.service.PricingService;
import com.project.w_3d_world.features.ai.util.GlbStats;
import com.project.w_3d_world.features.ai.util.ImageProbe;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.enums.ModelType;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

@Slf4j
@Service
public class AiTaskServiceImpl implements AiTaskService {

    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20 MB
    private static final int MIN_IMAGE_SIDE = 64;
    private static final int MAX_IMAGE_SIDE = 4096;

    private final AiRequestRepository aiRequestRepository;
    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ModelStorageService storageService;
    private final ProviderRegistry providerRegistry;
    private final PricingService pricingService;
    private final TransactionTemplate txTemplate;
    private final int defaultCreditsPerGeneration;
    private final String webhookSecret;
    private final AiWalletService aiWalletService;
    private final AiUploadService aiUploadService;
    private final AiExportService aiExportService;

    @Autowired
    public AiTaskServiceImpl(
            AiRequestRepository aiRequestRepository,
            Model3DRepository model3DRepository,
            ModelVersionRepository modelVersionRepository,
            ModelFileRepository modelFileRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            ModelStorageService storageService,
            ProviderRegistry providerRegistry,
            PricingService pricingService,
            PlatformTransactionManager transactionManager,
            @Value("${app.ai.credits-per-generation:0}") int defaultCreditsPerGeneration,
            @Value("${app.ai.webhook-secret:}") String webhookSecret,
            AiWalletService aiWalletService,
            AiUploadService aiUploadService,
            AiExportService aiExportService
    ) {
        this.aiRequestRepository = aiRequestRepository;
        this.model3DRepository = model3DRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.modelFileRepository = modelFileRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageService = storageService;
        this.providerRegistry = providerRegistry;
        this.pricingService = pricingService;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.defaultCreditsPerGeneration = Math.max(0, defaultCreditsPerGeneration);
        this.webhookSecret = webhookSecret;
        this.aiWalletService = aiWalletService;
        this.aiUploadService = aiUploadService;
        this.aiExportService = aiExportService;
    }

    public AiTaskServiceImpl(
            AiRequestRepository aiRequestRepository,
            Model3DRepository model3DRepository,
            ModelVersionRepository modelVersionRepository,
            ModelFileRepository modelFileRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            ModelStorageService storageService,
            ProviderRegistry providerRegistry,
            PricingService pricingService,
            TransactionTemplate txTemplate,
            int defaultCreditsPerGeneration,
            String webhookSecret,
            AiWalletService aiWalletService,
            AiUploadService aiUploadService,
            AiExportService aiExportService
    ) {
        this.aiRequestRepository = aiRequestRepository;
        this.model3DRepository = model3DRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.modelFileRepository = modelFileRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageService = storageService;
        this.providerRegistry = providerRegistry;
        this.pricingService = pricingService;
        this.txTemplate = txTemplate;
        this.defaultCreditsPerGeneration = Math.max(0, defaultCreditsPerGeneration);
        this.webhookSecret = webhookSecret;
        this.aiWalletService = aiWalletService;
        this.aiUploadService = aiUploadService;
        this.aiExportService = aiExportService;
    }

    private boolean isExplicitProviderRejection(Exception err) {
        if (err instanceof com.project.w_3d_world.features.ai.provider.ProviderException pe) {
            int code = pe.getHttpStatus();
            return code >= 400 && code < 500 && code != 408 && code != 429;
        }
        return false;
    }

    private static String checkedName(String name, String fallback) {
        String val = (name == null || name.isBlank()) ? fallback : name.trim();
        if (val.length() > 255) {
            val = val.substring(0, 255);
        }
        return val;
    }

    private record InitialAiRequestData(AiRequest aiRequest, Model3D model, int creditCost) {}

    @Override
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
                if (isExplicitProviderRejection(err)) {
                    aiReq.markFailed("Provider rejected: " + (err != null ? err.getMessage() : "Unknown error"));
                    if (aiWalletService != null) {
                        aiWalletService.refundAiCreditsOnce(aiReq, userId, "Provider rejected submission: " + (err != null ? err.getMessage() : ""));
                    }
                } else {
                    aiReq.markProcessing(null);
                    aiReq.setFailureReason("PROVIDER_TIMEOUT_AWAITING_RECONCILIATION: " + (err != null ? err.getMessage() : "Unknown error"));
                    log.warn("Submission timed out or network error for request #{}; keeping in PROCESSING without automatic refund", aiReq.getAiRequestId());
                }
                aiRequestRepository.saveAndFlush(aiReq);
            });
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_TEXT_TO_3D_REQUESTED", "AI_REQUEST",
                initData.aiRequest().getAiRequestId(), "Prompt: " + (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt), null
        ));

        return detail(userId, initData.aiRequest().getAiRequestId());
    }

    @Override
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

        User preCheckUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        if (creditCost > 0 && preCheckUser.getAiCreditsBalance() < creditCost) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Insufficient AI credits: required " + creditCost + ", available " + preCheckUser.getAiCreditsBalance());
        }

        // Stage input image in user-isolated staged memory store
        String ext = mime.contains("png") ? "png" : (mime.contains("webp") ? "webp" : "jpg");
        String inputId = UUID.randomUUID().toString();
        String inputStorageKey = "ai/inputs/" + userId + "/" + inputId + "." + ext;
        if (aiUploadService != null) {
            aiUploadService.stageImage(inputId, new StagedUpload(inputId, userId, originalName, mime, bytes.length, bytes, Instant.now()));
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
                if (isExplicitProviderRejection(err)) {
                    aiReq.markFailed("Provider rejected: " + (err != null ? err.getMessage() : "Unknown error"));
                    if (aiWalletService != null) {
                        aiWalletService.refundAiCreditsOnce(aiReq, userId, "Provider rejected submission: " + (err != null ? err.getMessage() : ""));
                    }
                } else {
                    aiReq.markProcessing(null);
                    aiReq.setFailureReason("PROVIDER_TIMEOUT_AWAITING_RECONCILIATION: " + (err != null ? err.getMessage() : "Unknown error"));
                    log.warn("Image submission timed out or network error for request #{}; keeping in PROCESSING without automatic refund", aiReq.getAiRequestId());
                }
                aiRequestRepository.saveAndFlush(aiReq);
            });
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_IMAGE_TO_3D_REQUESTED", "AI_REQUEST",
                initData.aiRequest().getAiRequestId(), "File: " + originalName, null
        ));

        return detail(userId, initData.aiRequest().getAiRequestId());
    }

    @Override
    public AiTaskDetailResponse createUnifiedTask(int userId, CreateAiUnifiedTaskRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        String mode = request.mode() != null ? request.mode().trim().toUpperCase(Locale.ROOT) : "TEXT_TO_3D";
        boolean isImageMode = "IMAGE_TO_3D".equalsIgnoreCase(mode) || "SINGLE".equalsIgnoreCase(mode)
                || "MULTI".equalsIgnoreCase(mode) || (request.uploadIds() != null && !request.uploadIds().isEmpty());

        if (isImageMode) {
            if (request.consent() != null && !request.consent()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED: Confirmation of image rights is required.");
            }
            if (request.uploadIds() == null || request.uploadIds().isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGES: At least one uploaded image ID is required.");
            }
            String uploadId = request.uploadIds().get(0);
            StagedUpload staged = aiUploadService != null ? aiUploadService.getStagedImage(uploadId) : null;
            if (staged == null || staged.userId() != userId) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGES: Uploaded image not found or expired.");
            }

            int variants = (request.variants() != null && request.variants() > 0) ? request.variants() : 1;
            Map<String, Object> normConfig = pricingService.normalizeConfig(request.config(), "single");
            Map<String, Object> est = pricingService.estimate(normConfig, variants);
            int creditCost = defaultCreditsPerGeneration > 0 ? defaultCreditsPerGeneration : ((Number) est.get("total")).intValue();

            if (request.quotedCredits() != null && request.quotedCredits().intValue() != creditCost) {
                throw new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED: Estimated credits (" + creditCost + ") do not match quoted credits (" + request.quotedCredits() + ").");
            }

            User userCheck = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
            if (creditCost > 0 && userCheck.getAiCreditsBalance() < creditCost) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_CREDITS: Required " + creditCost + ", available " + userCheck.getAiCreditsBalance());
            }

            String originalName = staged.filename();
            String modelName = checkedName(request.name(), originalName);
            String prompt = request.prompt() != null ? request.prompt().trim() : "Image-to-3D: " + originalName;
            String ext = staged.mimeType().contains("png") ? "png" : (staged.mimeType().contains("webp") ? "webp" : "jpg");
            String inputStorageKey = "ai/inputs/" + userId + "/" + uploadId + "." + ext;

            ProviderAdapter provider = providerRegistry.get();

            InitialAiRequestData initData = txTemplate.execute(status -> {
                User u = userRepository.lockById(userId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
                if (!u.isActive()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active.");
                }
                if (creditCost > 0) {
                    u.deductAiCredits(creditCost);
                    userRepository.saveAndFlush(u);
                }

                Model3D model;
                if (request.projectId() != null) {
                    model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(request.projectId(), userId)
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found."));
                } else {
                    model = model3DRepository.saveAndFlush(new Model3D(
                            userId,
                            modelName,
                            prompt,
                            ModelType.AI_GENERATOR,
                            ModelOriginType.AI,
                            "PRIVATE"
                    ));
                }

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

            String providerJobId = null;
            Exception providerError = null;
            try {
                providerJobId = provider.submitImage(staged.data(), staged.mimeType(), normConfig);
            } catch (Exception e) {
                providerError = e;
            }

            final String finalJobId = providerJobId;
            final Exception finalError = providerError;

            txTemplate.executeWithoutResult(status -> {
                AiRequest aiReq = aiRequestRepository.lockByIdAndUserId(initData.aiRequest().getAiRequestId(), userId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI request not found."));
                if (finalJobId != null && !finalJobId.isBlank()) {
                    aiReq.markProcessing(finalJobId);
                    aiRequestRepository.saveAndFlush(aiReq);
                } else {
                    if (isExplicitProviderRejection(finalError)) {
                        aiReq.markFailed("Provider rejected task: " + finalError.getMessage());
                        if (aiWalletService != null) {
                            aiWalletService.refundAiCreditsOnce(aiReq, userId, "Provider rejected task");
                        }
                    } else {
                        aiReq.setFailureReason("PROVIDER_TIMEOUT: Task submitted to provider but acknowledgement was lost or timed out.");
                    }
                    aiRequestRepository.saveAndFlush(aiReq);
                }
            });

            return detail(userId, initData.aiRequest().getAiRequestId());
        }

        GenerateTextRequest textReq = new GenerateTextRequest(request.name(), request.prompt(), request.config());
        return generateText(userId, textReq);
    }

    @Override
    @Transactional
    public AiTaskDetailResponse refresh(int userId, int requestId) {
        return txTemplate.execute(txStatus -> {
            AiRequest request = aiRequestRepository.lockByIdAndUserId(requestId, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI request not found."));

            if ("SUCCESS".equals(request.getStatus()) || "FAILED".equals(request.getStatus())) {
                return detail(userId, requestId);
            }

            String externalId = request.getProviderJobReference();
            if (externalId == null || externalId.isBlank()) {
                // Task has no provider reference (e.g. server restarted or timeout occurred)
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
                if (aiWalletService != null) {
                    aiWalletService.refundAiCreditsOnce(request, userId, "Task failed on provider: " + request.getFailureReason());
                }
                aiRequestRepository.saveAndFlush(request);
                auditLogRepository.save(new AuditLog(
                        userId, "AI_REQUEST_FAILED", "AI_REQUEST",
                        requestId, "Reason: " + request.getFailureReason(), null
                ));
            } else {
                // Provider is active and processing. If there was a temporary timeout note, clear it
                if (request.getFailureReason() != null && request.getFailureReason().startsWith("PROVIDER_TIMEOUT")) {
                    request.setFailureReason(null);
                    aiRequestRepository.saveAndFlush(request);
                }
            }

            return detail(userId, requestId);
        });
    }

    @Override
    @Transactional
    public AiTaskDetailResponse cancel(int userId, int requestId) {
        return txTemplate.execute(txStatus -> {
            AiRequest request = aiRequestRepository.lockByIdAndUserId(requestId, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI request not found."));

            if ("SUCCESS".equals(request.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "Cannot cancel already completed generation task.");
            }
            if ("FAILED".equals(request.getStatus())) {
                return detail(userId, requestId);
            }

            ProviderAdapter provider = providerRegistry.get(request.getProviderName());
            if (request.getProviderJobReference() != null && !request.getProviderJobReference().isBlank()) {
                // Check provider status first to safely resolve SUCCESS vs CANCEL race
                ProviderTask task = null;
                try {
                    task = provider.getTask(request.getProviderJobReference(), request.getRequestType());
                } catch (Exception e) {
                    log.warn("Failed to check provider task status during cancel: {}", e.getMessage());
                }

                if (task != null && task.isSuccess()) {
                    // Provider completed before cancellation arrived: finalize generation without refund
                    Model3D model = model3DRepository.findById(request.getModelId()).orElse(null);
                    if (model != null) {
                        completeGeneration(request, model, provider, task);
                    }
                    return detail(userId, requestId);
                }

                boolean cancelConfirmed = true;
                try {
                    provider.cancel(request.getProviderJobReference(), request.getRequestType());
                } catch (Exception e) {
                    log.warn("Provider cancellation returned error: {}", e.getMessage());
                    if (e instanceof com.project.w_3d_world.features.ai.provider.ProviderException pe && pe.isRetryable()) {
                        cancelConfirmed = false;
                    }
                }

                if (!cancelConfirmed) {
                    request.setFailureReason("CANCEL_PENDING_PROVIDER_CONFIRMATION");
                    aiRequestRepository.saveAndFlush(request);
                    return detail(userId, requestId);
                }
            }

            request.markFailed("Cancelled by user");
            if (aiWalletService != null) {
                aiWalletService.refundAiCreditsOnce(request, userId, "User initiated cancellation");
            }
            aiRequestRepository.saveAndFlush(request);

            auditLogRepository.save(new AuditLog(
                    userId, "AI_REQUEST_CANCELLED", "AI_REQUEST",
                    requestId, "User initiated cancellation", null
            ));

            return detail(userId, requestId);
        });
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

        // Attempt native storage of additional formats if provided by provider
        if (task.modelUrls() != null && aiExportService != null) {
            aiExportService.storeNativeExtraFormats(model, version, provider, task);
        }

        request.markSuccess();
        aiRequestRepository.saveAndFlush(request);

        auditLogRepository.save(new AuditLog(
                request.getUserId(), "AI_MODEL_GENERATED", "MODEL",
                model.getModelId(), "Generated v" + nextVersion + " from AI Request #" + request.getAiRequestId(), null
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiTaskDetailResponse> list(int userId) {
        List<AiRequest> requests = aiRequestRepository.findByUserIdOrderByCreatedAtDescAiRequestIdDesc(userId);
        return requests.stream().map(r -> mapDetail(userId, r)).toList();
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public Map<String, Object> handleMeshyWebhook(Map<String, Object> payload, String secret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Meshy webhook is disabled: unconfigured secret and unverified provider signature. Enforce client polling.");
        }
        if (secret == null || !java.security.MessageDigest.isEqual(
                webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret.");
        }
        if (payload == null || !payload.containsKey("id")) {
            return Map.of("status", "IGNORED", "message", "No task id found in payload");
        }

        String externalTaskId = String.valueOf(payload.get("id"));
        log.info("Received Meshy webhook for task {}", externalTaskId);

        List<AiRequest> matching = aiRequestRepository.findAll().stream()
                .filter(r -> Objects.equals(r.getProviderJobReference(), externalTaskId)
                        && ("PENDING".equals(r.getStatus()) || "PROCESSING".equals(r.getStatus())))
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
