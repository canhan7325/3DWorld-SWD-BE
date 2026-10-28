package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.features.ai.dto.request.*;
import com.project.w_3d_world.features.ai.dto.response.*;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.*;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * High-level Facade delegating to specialized AI domain services:
 * - {@link AiTaskService} for AI 3D task lifecycle, polling, webhooks, estimation, and configuration.
 * - {@link AiUploadService} for source image uploads, file listing, downloads, and staging cache.
 * - {@link AiProjectService} for AI-generated model projects, versions, and mesh dimensions.
 * - {@link AiExportService} for multi-format exports (GLB, STL, OBJ with ZIP packaging, FBX) and asset downloads.
 * - {@link AiWalletService} for user AI credits, demo topups, credit reservations, and refunds.
 */
@Slf4j
@Service
public class AiGenerationServiceImpl implements AiGenerationService {

    private final AiTaskService aiTaskService;
    private final AiUploadService aiUploadService;
    private final AiProjectService aiProjectService;
    private final AiExportService aiExportService;
    private final AiWalletService aiWalletService;

    @Autowired
    public AiGenerationServiceImpl(
            AiTaskService aiTaskService,
            AiUploadService aiUploadService,
            AiProjectService aiProjectService,
            AiExportService aiExportService,
            AiWalletService aiWalletService
    ) {
        this.aiTaskService = aiTaskService;
        this.aiUploadService = aiUploadService;
        this.aiProjectService = aiProjectService;
        this.aiExportService = aiExportService;
        this.aiWalletService = aiWalletService;
    }

    /**
     * Backward-compatible 16-argument constructor for existing tests and direct instantiations.
     */
    public AiGenerationServiceImpl(
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
            int defaultCreditsPerGeneration,
            boolean demoTopupEnabled,
            String webhookSecret,
            ModelConverterService modelConverterService
    ) {
        AiWalletService walletSvc = new AiWalletServiceImpl(userRepository, aiRequestRepository, auditLogRepository, environment, demoTopupEnabled);
        AiUploadService uploadSvc = new AiUploadServiceImpl(auditLogRepository, aiRequestRepository, storageService);
        AiProjectService projectSvc = new AiProjectServiceImpl(model3DRepository, modelVersionRepository, modelFileRepository, aiRequestRepository, storageService, auditLogRepository);
        AiExportService exportSvc = new AiExportServiceImpl(model3DRepository, modelVersionRepository, modelFileRepository, aiRequestRepository, storageService, downloadService, providerRegistry, modelConverterService);
        AiTaskService taskSvc = new AiTaskServiceImpl(
                aiRequestRepository, model3DRepository, modelVersionRepository, modelFileRepository,
                userRepository, auditLogRepository, storageService, providerRegistry, pricingService,
                new TransactionTemplate(transactionManager), defaultCreditsPerGeneration, webhookSecret,
                walletSvc, uploadSvc, exportSvc
        );

        this.aiTaskService = taskSvc;
        this.aiUploadService = uploadSvc;
        this.aiProjectService = projectSvc;
        this.aiExportService = exportSvc;
        this.aiWalletService = walletSvc;
    }

    /**
     * Backward-compatible 15-argument constructor.
     */
    public AiGenerationServiceImpl(
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
            int defaultCreditsPerGeneration,
            boolean demoTopupEnabled,
            String webhookSecret
    ) {
        this(
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
                environment,
                defaultCreditsPerGeneration,
                demoTopupEnabled,
                webhookSecret,
                null
        );
    }

    /**
     * Backward-compatible 10-argument constructor.
     */
    public AiGenerationServiceImpl(
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
                null,
                null
        );
    }

    public record StagedUpload(
            String uploadId,
            int userId,
            String filename,
            String mimeType,
            long fileSize,
            byte[] data,
            Instant createdAt
    ) {}

    // ---------------- AiTaskService delegations ----------------

    @Override
    public AiTaskDetailResponse generateText(int userId, GenerateTextRequest request) {
        return aiTaskService.generateText(userId, request);
    }

    @Override
    public AiTaskDetailResponse generateImage(int userId, String name, String prompt, MultipartFile file) {
        return aiTaskService.generateImage(userId, name, prompt, file);
    }

    @Override
    public AiTaskDetailResponse createUnifiedTask(int userId, CreateAiUnifiedTaskRequest request) {
        return aiTaskService.createUnifiedTask(userId, request);
    }

    @Override
    public AiTaskDetailResponse refresh(int userId, int requestId) {
        return aiTaskService.refresh(userId, requestId);
    }

    @Override
    public AiTaskDetailResponse cancel(int userId, int requestId) {
        return aiTaskService.cancel(userId, requestId);
    }

    @Override
    public List<AiTaskDetailResponse> list(int userId) {
        return aiTaskService.list(userId);
    }

    @Override
    public List<AiTaskDetailResponse> listTasksFiltered(int userId, String status, int page, int size) {
        return aiTaskService.listTasksFiltered(userId, status, page, size);
    }

    @Override
    public AiTaskDetailResponse detail(int userId, int requestId) {
        return aiTaskService.detail(userId, requestId);
    }

    @Override
    public AiConfigOptionsResponse configOptions() {
        return aiTaskService.configOptions();
    }

    @Override
    public AiEstimateResponse estimate(int userId, AiEstimateRequest request) {
        return aiTaskService.estimate(userId, request);
    }

    @Override
    public Map<String, Object> handleMeshyWebhook(Map<String, Object> payload, String secret) {
        return aiTaskService.handleMeshyWebhook(payload, secret);
    }

    // ---------------- AiWalletService delegations ----------------

    @Override
    public AiWalletResponse wallet(int userId) {
        return aiWalletService.wallet(userId);
    }

    @Override
    public AiWalletResponse topup(int userId, AiTopupRequest request) {
        return aiWalletService.topup(userId, request);
    }

    // ---------------- AiUploadService delegations ----------------

    @Override
    public AiUploadResponse uploadSourceImage(int userId, MultipartFile file) {
        return aiUploadService.uploadSourceImage(userId, file);
    }

    @Override
    public List<AiUploadResponse> listUploads(int userId) {
        return aiUploadService.listUploads(userId);
    }

    @Override
    public AuthorizedModelDownload downloadUploadFile(int userId, String uploadId) {
        return aiUploadService.downloadUploadFile(userId, uploadId);
    }

    // ---------------- AiProjectService delegations ----------------

    @Override
    public List<AiProjectSummaryResponse> listProjects(int userId) {
        return aiProjectService.listProjects(userId);
    }

    @Override
    public AiProjectDetailResponse getProject(int userId, int modelId) {
        return aiProjectService.getProject(userId, modelId);
    }

    @Override
    public AiProjectDetailResponse updateProject(int userId, int modelId, UpdateAiProjectRequest request) {
        return aiProjectService.updateProject(userId, modelId, request);
    }

    @Override
    public AiVersionDetailResponse getVersion(int userId, int versionId) {
        return aiProjectService.getVersion(userId, versionId);
    }

    @Override
    public AiDimensionsResponse computeDimensions(int userId, int versionId, AiDimensionsRequest request) {
        return aiProjectService.computeDimensions(userId, versionId, request);
    }

    // ---------------- AiExportService delegations ----------------

    @Override
    public AiExportResponse exportVersion(int userId, int versionId, AiExportRequest request) {
        return aiExportService.exportVersion(userId, versionId, request);
    }

    @Override
    public AuthorizedModelDownload downloadAsset(int userId, int fileId) {
        return aiExportService.downloadAsset(userId, fileId);
    }
}
