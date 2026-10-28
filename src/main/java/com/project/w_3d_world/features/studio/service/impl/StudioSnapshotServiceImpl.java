package com.project.w_3d_world.features.studio.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.enums.ModelSourceType;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.studio.dto.request.CreateStudioSnapshotRequest;
import com.project.w_3d_world.features.studio.dto.request.UpdateStudioSnapshotRequest;
import com.project.w_3d_world.features.studio.dto.response.StudioSnapshotResponse;
import com.project.w_3d_world.features.studio.service.StudioSnapshotService;
import com.project.w_3d_world.features.studio.util.StudioSceneHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudioSnapshotServiceImpl implements StudioSnapshotService {

    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final ModelStorageService storageService;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StudioSnapshotResponse> listSnapshots(int userId, int modelId) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumberDesc(modelId);
        List<StudioSnapshotResponse> result = new ArrayList<>();

        for (ModelVersion v : versions) {
            List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(v.getModelVersionId());
            ModelFile targetFile = files.stream()
                    .filter(f -> f.getFileRole() == ModelFileRole.VIEWABLE_MODEL || f.getFileRole() == ModelFileRole.EDITABLE_SCENE)
                    .findFirst()
                    .orElse(null);

            String code = "SNAP-" + modelId + "-v" + v.getVersionNumber();
            String format = targetFile != null ? targetFile.getFileFormat() : "JSON";
            Long size = targetFile != null ? targetFile.getFileSize() : 0L;
            String downloadUrl = targetFile != null ? "/api/studio/projects/" + modelId + "/files/" + targetFile.getFileId() + "/download" : null;

            result.add(new StudioSnapshotResponse(
                    code,
                    modelId,
                    v.getVersionNumber(),
                    v.getChangeNote(),
                    format,
                    size,
                    v.getCreatedAt(),
                    downloadUrl
            ));
        }

        return result;
    }

    @Override
    @Transactional
    public StudioSnapshotResponse createSnapshot(int userId, int modelId, CreateStudioSnapshotRequest request) {
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project has no base version."));

        int nextVer = latest.getVersionNumber() + 1;
        String note = (request != null && request.note() != null && !request.note().isBlank())
                ? request.note().trim()
                : "Snapshot export v" + nextVer;

        ModelVersion snapVersion = modelVersionRepository.saveAndFlush(new ModelVersion(
                modelId,
                latest.getModelVersionId(),
                null,
                userId,
                nextVer,
                ModelSourceType.EDITOR_SAVE,
                note
        ));

        // Copy scene or primary asset from latest
        byte[] assetBytes = null;
        String format = "json";
        String mime = "application/json";
        List<ModelFile> files = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(latest.getModelVersionId());
        for (ModelFile f : files) {
            if (f.getFileRole() == ModelFileRole.EDITABLE_SCENE || f.getFileRole() == ModelFileRole.VIEWABLE_MODEL) {
                try (InputStream stream = storageService.open(f.getStorageKey())) {
                    assetBytes = stream.readAllBytes();
                    format = f.getFileFormat();
                    mime = f.getMimeType();
                    break;
                } catch (Exception ignored) {}
            }
        }

        if (assetBytes == null) {
            assetBytes = StudioSceneHelper.serializeScene(Map.of("nodes", List.of()));
        }

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(assetBytes)) {
            stored = storageService.store(
                    in,
                    "snapshot." + format.toLowerCase(Locale.ROOT),
                    mime,
                    ModelFileRole.VIEWABLE_MODEL,
                    modelId,
                    snapVersion.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store snapshot asset: " + e.getMessage());
        }

        ModelFile snapFile = modelFileRepository.saveAndFlush(new ModelFile(
                snapVersion.getModelVersionId(),
                ModelFileRole.VIEWABLE_MODEL,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_SNAPSHOT_CREATED", "MODEL",
                modelId, "Created snapshot " + snapVersion.getVersionNumber() + " (" + note + ")", null
        ));

        String code = "SNAP-" + modelId + "-v" + snapVersion.getVersionNumber();
        String downloadUrl = "/api/studio/projects/" + modelId + "/files/" + snapFile.getFileId() + "/download";

        return new StudioSnapshotResponse(
                code,
                modelId,
                snapVersion.getVersionNumber(),
                note,
                stored.fileFormat(),
                stored.fileSize(),
                snapVersion.getCreatedAt(),
                downloadUrl
        );
    }

    @Override
    @Transactional
    public StudioSnapshotResponse createSnapshotWithFile(int userId, int modelId, CreateStudioSnapshotRequest meta, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Snapshot STL file is required.");
        }
        Model3D model = model3DRepository.findOwnedActiveByIdForUpdate(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        ModelVersion latest = modelVersionRepository.findTopByModelIdOrderByVersionNumberDesc(modelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project has no base version."));

        int nextVer = latest.getVersionNumber() + 1;
        String note = (meta != null && meta.note() != null && !meta.note().isBlank())
                ? meta.note().trim()
                : "Snapshot export v" + nextVer;

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read uploaded snapshot file.");
        }

        if (fileBytes.length < 15) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid STL file: content too small.");
        }

        String computedHash;
        try {
            computedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fileBytes));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to hash snapshot file: " + e.getMessage());
        }

        ModelVersion snapVersion = modelVersionRepository.saveAndFlush(new ModelVersion(
                modelId,
                latest.getModelVersionId(),
                null,
                userId,
                nextVer,
                ModelSourceType.EDITOR_SAVE,
                note
        ));

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            stored = storageService.store(
                    in,
                    "snapshot-v" + nextVer + ".stl",
                    "model/stl",
                    ModelFileRole.SOURCE_MODEL,
                    modelId,
                    snapVersion.getModelVersionId()
            );
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store snapshot STL file: " + e.getMessage());
        }

        ModelFile snapFile = modelFileRepository.saveAndFlush(new ModelFile(
                snapVersion.getModelVersionId(),
                ModelFileRole.SOURCE_MODEL,
                stored.fileFormat(),
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                true
        ));

        auditLogRepository.save(new AuditLog(
                userId, "STUDIO_SNAPSHOT_CREATED", "MODEL",
                modelId, "Created STL snapshot " + snapVersion.getVersionNumber() + " (" + note + ")", null
        ));

        String code = "SNAP-" + modelId + "-v" + snapVersion.getVersionNumber();
        String downloadUrl = "/api/studio/projects/" + modelId + "/files/" + snapFile.getFileId() + "/download";

        return new StudioSnapshotResponse(
                code,
                modelId,
                snapVersion.getVersionNumber(),
                note,
                stored.fileFormat(),
                stored.fileSize(),
                snapVersion.getCreatedAt(),
                downloadUrl,
                code,
                snapVersion.getVersionNumber(),
                computedHash,
                meta != null ? meta.dims() : null,
                meta != null ? meta.parts() : 1,
                meta != null ? meta.triangles() : null,
                "READY",
                meta != null && meta.unit() != null ? meta.unit() : "mm",
                stored.fileSize(),
                meta != null && meta.acknowledgedWarnings() != null ? meta.acknowledgedWarnings() : List.of(),
                meta != null ? meta.consentVersion() : "2026-10"
        );
    }

    @Override
    @Transactional
    public StudioSnapshotResponse updateSnapshot(int userId, int modelId, String snapshotCode, UpdateStudioSnapshotRequest request) {
        model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Studio project not found."));

        if (snapshotCode == null || !snapshotCode.startsWith("SNAP-" + modelId + "-v")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid snapshot code for this project.");
        }

        int versionNumber;
        try {
            String verStr = snapshotCode.substring(snapshotCode.lastIndexOf("-v") + 2);
            versionNumber = Integer.parseInt(verStr);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot parse version number from snapshot code.");
        }

        ModelVersion version = modelVersionRepository.findByModelIdAndVersionNumber(modelId, versionNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Snapshot version not found."));

        if (request != null && request.note() != null) {
            version.updateChangeNote(request.note().trim());
            modelVersionRepository.saveAndFlush(version);
        }

        ModelFile targetFile = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(version.getModelVersionId())
                .stream()
                .filter(f -> f.getFileRole() == ModelFileRole.VIEWABLE_MODEL || f.getFileRole() == ModelFileRole.EDITABLE_SCENE)
                .findFirst()
                .orElse(null);

        String format = targetFile != null ? targetFile.getFileFormat() : "JSON";
        Long size = targetFile != null ? targetFile.getFileSize() : 0L;
        String downloadUrl = targetFile != null ? "/api/studio/projects/" + modelId + "/files/" + targetFile.getFileId() + "/download" : null;

        return new StudioSnapshotResponse(
                snapshotCode,
                modelId,
                version.getVersionNumber(),
                version.getChangeNote(),
                format,
                size,
                version.getCreatedAt(),
                downloadUrl
        );
    }
}
