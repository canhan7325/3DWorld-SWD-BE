package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.AiDimensionsRequest;
import com.project.w_3d_world.features.ai.dto.request.UpdateAiProjectRequest;
import com.project.w_3d_world.features.ai.dto.response.*;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiProjectService;
import com.project.w_3d_world.features.ai.util.GlbStats;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelType;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiProjectServiceImpl implements AiProjectService {

    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final AiRequestRepository aiRequestRepository;
    private final ModelStorageService storageService;
    private final AuditLogRepository auditLogRepository;

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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
}
