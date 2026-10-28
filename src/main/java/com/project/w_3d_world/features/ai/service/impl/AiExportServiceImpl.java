package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.request.AiExportRequest;
import com.project.w_3d_world.features.ai.dto.response.AiExportResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.provider.ProviderAdapter;
import com.project.w_3d_world.features.ai.provider.ProviderRegistry;
import com.project.w_3d_world.features.ai.provider.ProviderTask;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiExportService;
import com.project.w_3d_world.features.ai.service.ModelConverterService;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.StoredModelFile;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class AiExportServiceImpl implements AiExportService {

    private final Model3DRepository model3DRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelFileRepository modelFileRepository;
    private final AiRequestRepository aiRequestRepository;
    private final ModelStorageService storageService;
    private final ModelDownloadService downloadService;
    private final ProviderRegistry providerRegistry;
    private final ModelConverterService modelConverterService;

    @Autowired
    public AiExportServiceImpl(
            Model3DRepository model3DRepository,
            ModelVersionRepository modelVersionRepository,
            ModelFileRepository modelFileRepository,
            AiRequestRepository aiRequestRepository,
            ModelStorageService storageService,
            ModelDownloadService downloadService,
            ProviderRegistry providerRegistry,
            @Autowired(required = false) ModelConverterService modelConverterService
    ) {
        this.model3DRepository = model3DRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.modelFileRepository = modelFileRepository;
        this.aiRequestRepository = aiRequestRepository;
        this.storageService = storageService;
        this.downloadService = downloadService;
        this.providerRegistry = providerRegistry;
        this.modelConverterService = modelConverterService;
    }

    @Override
    @Transactional
    public AiExportResponse exportVersion(int userId, int versionId, AiExportRequest request) {
        String format = (request != null && request.format() != null && !request.format().isBlank())
                ? request.format().trim().toUpperCase(Locale.ROOT)
                : "GLB";
        if (!Set.of("GLB", "STL", "OBJ", "FBX").contains(format)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported export format '" + format + "'. Supported formats are GLB, STL, OBJ, FBX.");
        }

        ModelVersion version = modelVersionRepository.findById(versionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version not found."));
        Model3D model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(version.getModelId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found or access denied."));

        List<ModelFile> existingFiles = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(versionId);

        // Check if file already exists
        ModelFile matched = existingFiles.stream()
                .filter(f -> f.getFileFormat().equalsIgnoreCase(format) ||
                        ("OBJ".equals(format) && "ZIP".equalsIgnoreCase(f.getFileFormat())))
                .findFirst()
                .orElse(null);

        String baseTitle = (model.getModelName() != null && !model.getModelName().isBlank())
                ? model.getModelName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : ("model-" + model.getModelId());

        if (matched != null) {
            boolean isZip = "ZIP".equalsIgnoreCase(matched.getFileFormat());
            String fileName = baseTitle + "." + (isZip ? "zip" : format.toLowerCase(Locale.ROOT));
            String downloadUrl = "/api/ai-studio/assets/" + matched.getFileId() + "/file";
            return new AiExportResponse(
                    matched.getFileId(),
                    format,
                    fileName,
                    matched.getFileSize(),
                    downloadUrl,
                    matched.getFileHash(),
                    Instant.now()
            );
        }

        if ("GLB".equals(format)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No GLB file found for version " + versionId);
        }

        // 1. Check if provider task can provide the format directly
        byte[] acquiredBytes = null;
        String acquiredFilename = null;
        String acquiredMime = null;
        String storedFormat = format;

        if (version.getAiRequestId() != null) {
            AiRequest aiReq = aiRequestRepository.findById(version.getAiRequestId()).orElse(null);
            if (aiReq != null && aiReq.getProviderJobReference() != null && providerRegistry != null) {
                ProviderAdapter provider = providerRegistry.get(aiReq.getProviderName());
                try {
                    ProviderTask pt = provider.getTask(aiReq.getProviderJobReference(), aiReq.getRequestType());
                    String formatKey = format.toLowerCase(Locale.ROOT);
                    if (pt != null && pt.modelUrls() != null && pt.modelUrls().containsKey(formatKey)) {
                        String url = pt.modelUrls().get(formatKey);
                        if (url != null && !url.isBlank()) {
                            if ("OBJ".equals(format)) {
                                String mtlUrl = pt.modelUrls().get("mtl");
                                List<Map<String, String>> textures = pt.textureUrls();
                                byte[] objBytes = provider.downloadAsset(url);
                                if (objBytes != null && objBytes.length > 0) {
                                    if ((mtlUrl != null && !mtlUrl.isBlank()) || (textures != null && !textures.isEmpty())) {
                                        acquiredBytes = packageObjWithMtl(provider, objBytes, mtlUrl, textures);
                                        acquiredFilename = "model.zip";
                                        acquiredMime = "application/zip";
                                        storedFormat = "ZIP";
                                    } else {
                                        acquiredBytes = objBytes;
                                        acquiredFilename = "model.obj";
                                        acquiredMime = "model/obj";
                                        storedFormat = "OBJ";
                                    }
                                }
                            } else {
                                acquiredBytes = provider.downloadAsset(url);
                                acquiredFilename = "model." + formatKey;
                                acquiredMime = "STL".equals(format) ? "model/stl" : "application/octet-stream";
                                storedFormat = format;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not retrieve native format {} from provider for AI request #{}: {}", format, aiReq.getAiRequestId(), e.getMessage());
                }
            }
        }

        // 2. Conversion fallback
        if (acquiredBytes == null) {
            ModelFile primaryGlb = existingFiles.stream()
                    .filter(f -> "GLB".equalsIgnoreCase(f.getFileFormat()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No GLB base model found for version " + versionId));

            if (modelConverterService == null || !modelConverterService.isConversionAvailable()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "FORMAT_UNAVAILABLE: Export format '" + format + "' is not natively available and conversion service is not configured.");
            }

            byte[] glbBytes;
            try (InputStream in = storageService.open(primaryGlb.getStorageKey())) {
                glbBytes = in.readAllBytes();
            } catch (Exception e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read base GLB model for conversion: " + e.getMessage());
            }

            acquiredBytes = modelConverterService.convertGlb(glbBytes, format);
            if ("OBJ".equals(format) && isZipBytes(acquiredBytes)) {
                acquiredFilename = "model.zip";
                acquiredMime = "application/zip";
                storedFormat = "ZIP";
            } else {
                acquiredFilename = "model." + format.toLowerCase(Locale.ROOT);
                acquiredMime = switch (format) {
                    case "STL" -> "model/stl";
                    case "OBJ" -> "model/obj";
                    case "FBX" -> "application/octet-stream";
                    default -> "model/gltf-binary";
                };
                storedFormat = format;
            }
        }

        StoredModelFile stored;
        try (InputStream in = new ByteArrayInputStream(acquiredBytes)) {
            stored = storageService.store(
                    in,
                    acquiredFilename,
                    acquiredMime,
                    ModelFileRole.SOURCE_MODEL,
                    model.getModelId(),
                    version.getModelVersionId()
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store exported asset: " + e.getMessage());
        }

        ModelFile saved = modelFileRepository.saveAndFlush(new ModelFile(
                version.getModelVersionId(),
                ModelFileRole.SOURCE_MODEL,
                storedFormat,
                stored.storageKey(),
                stored.mimeType(),
                stored.fileHash(),
                stored.fileSize(),
                false
        ));

        boolean isZip = "ZIP".equalsIgnoreCase(saved.getFileFormat());
        String fileName = baseTitle + "." + (isZip ? "zip" : format.toLowerCase(Locale.ROOT));
        String downloadUrl = "/api/ai-studio/assets/" + saved.getFileId() + "/file";
        return new AiExportResponse(
                saved.getFileId(),
                format,
                fileName,
                saved.getFileSize(),
                downloadUrl,
                saved.getFileHash(),
                Instant.now()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadAsset(int userId, int fileId) {
        ModelFile file = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Asset file not found."));
        ModelVersion version = modelVersionRepository.findById(file.getModelVersionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model version not found."));
        Model3D model = model3DRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(version.getModelId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found or access denied."));
        if (downloadService == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Download service is not available.");
        }
        AuthorizedModelDownload rawDownload = downloadService.openAuthorizedDownload(userId, model.getModelId(), fileId);
        String ext = ModelServiceValidation.normalizeFileFormat(file.getFileFormat());
        String safeExt = ext.equalsIgnoreCase("zip") ? "zip" : (ext.isEmpty() ? "bin" : ext);
        String baseName = (model.getModelName() != null && !model.getModelName().isBlank())
                ? model.getModelName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : ("model-" + model.getModelId());
        String customFilename = baseName + "." + safeExt;
        return new AuthorizedModelDownload(rawDownload.inputStream(), customFilename, rawDownload.contentType(), rawDownload.fileSize());
    }

    @Override
    public void storeNativeExtraFormats(Model3D model, ModelVersion version, ProviderAdapter provider, ProviderTask task) {
        storeExtraFormatIfAvailable(model, version, provider, task, "stl", "STL", "model.stl", "model/stl");
        storeExtraFormatIfAvailable(model, version, provider, task, "fbx", "FBX", "model.fbx", "application/octet-stream");
        storeNativeObjIfAvailable(model, version, provider, task);
    }

    private void storeExtraFormatIfAvailable(
            Model3D model,
            ModelVersion version,
            ProviderAdapter provider,
            ProviderTask task,
            String urlKey,
            String fileFormat,
            String filename,
            String mimeType
    ) {
        String url = task.modelUrls().get(urlKey);
        if (url == null || url.isBlank()) return;
        try {
            boolean exists = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(version.getModelVersionId()).stream()
                    .anyMatch(f -> fileFormat.equalsIgnoreCase(f.getFileFormat()));
            if (exists) return;

            byte[] bytes = provider.downloadAsset(url);
            if (bytes == null || bytes.length == 0) return;

            StoredModelFile stored;
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                stored = storageService.store(
                        in,
                        filename,
                        mimeType,
                        ModelFileRole.SOURCE_MODEL,
                        model.getModelId(),
                        version.getModelVersionId()
                );
            }
            modelFileRepository.saveAndFlush(new ModelFile(
                    version.getModelVersionId(),
                    ModelFileRole.SOURCE_MODEL,
                    stored.fileFormat(),
                    stored.storageKey(),
                    stored.mimeType(),
                    stored.fileHash(),
                    stored.fileSize(),
                    false
            ));
            log.info("Saved native {} export for version #{}", fileFormat, version.getModelVersionId());
        } catch (Exception e) {
            log.warn("Failed to retrieve/store native {} from provider for version #{}: {}", fileFormat, version.getModelVersionId(), e.getMessage());
        }
    }

    private void storeNativeObjIfAvailable(
            Model3D model,
            ModelVersion version,
            ProviderAdapter provider,
            ProviderTask task
    ) {
        String objUrl = task.modelUrls().get("obj");
        if (objUrl == null || objUrl.isBlank()) return;
        try {
            boolean exists = modelFileRepository.findByModelVersionIdOrderByFileIdAsc(version.getModelVersionId()).stream()
                    .anyMatch(f -> "OBJ".equalsIgnoreCase(f.getFileFormat()) || "ZIP".equalsIgnoreCase(f.getFileFormat()));
            if (exists) return;

            byte[] objBytes = provider.downloadAsset(objUrl);
            if (objBytes == null || objBytes.length == 0) return;

            String mtlUrl = task.modelUrls().get("mtl");
            List<Map<String, String>> textures = task.textureUrls();

            byte[] finalBytes = objBytes;
            String filename = "model.obj";
            String mimeType = "model/obj";
            String fileFormat = "OBJ";

            if ((mtlUrl != null && !mtlUrl.isBlank()) || (textures != null && !textures.isEmpty())) {
                finalBytes = packageObjWithMtl(provider, objBytes, mtlUrl, textures);
                filename = "model.zip";
                mimeType = "application/zip";
                fileFormat = "ZIP";
            }

            StoredModelFile stored;
            try (InputStream in = new ByteArrayInputStream(finalBytes)) {
                stored = storageService.store(
                        in,
                        filename,
                        mimeType,
                        ModelFileRole.SOURCE_MODEL,
                        model.getModelId(),
                        version.getModelVersionId()
                );
            }
            modelFileRepository.saveAndFlush(new ModelFile(
                    version.getModelVersionId(),
                    ModelFileRole.SOURCE_MODEL,
                    fileFormat,
                    stored.storageKey(),
                    stored.mimeType(),
                    stored.fileHash(),
                    stored.fileSize(),
                    false
            ));
            log.info("Saved native OBJ export for version #{}", version.getModelVersionId());
        } catch (Exception e) {
            log.warn("Failed to retrieve/store native OBJ from provider for version #{}: {}", version.getModelVersionId(), e.getMessage());
        }
    }

    private byte[] packageObjWithMtl(
            ProviderAdapter provider,
            byte[] objBytes,
            String mtlUrl,
            List<Map<String, String>> textures
    ) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String objText = new String(objBytes, StandardCharsets.UTF_8);
            if (mtlUrl != null && !mtlUrl.isBlank()) {
                objText = objText.replaceFirst("mtllib\\s+.*", "mtllib model.mtl");
                if (!objText.contains("mtllib model.mtl")) {
                    objText = "mtllib model.mtl\n" + objText;
                }
            }
            zos.putNextEntry(new ZipEntry("model.obj"));
            zos.write(objText.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            if (mtlUrl != null && !mtlUrl.isBlank()) {
                try {
                    byte[] mtlBytes = provider.downloadAsset(mtlUrl);
                    if (mtlBytes != null && mtlBytes.length > 0) {
                        zos.putNextEntry(new ZipEntry("model.mtl"));
                        zos.write(mtlBytes);
                        zos.closeEntry();
                    }
                } catch (Exception mtlEx) {
                    log.warn("Failed to download MTL {}: {}", mtlUrl, mtlEx.getMessage());
                }
            }

            if (textures != null) {
                for (int i = 0; i < textures.size(); i++) {
                    Map<String, String> texMap = textures.get(i);
                    for (Map.Entry<String, String> entry : texMap.entrySet()) {
                        String tUrl = entry.getValue();
                        if (tUrl != null && !tUrl.isBlank()) {
                            try {
                                byte[] texBytes = provider.downloadAsset(tUrl);
                                if (texBytes != null && texBytes.length > 0) {
                                    String texName = "textures/" + entry.getKey() + "_" + i + ".png";
                                    zos.putNextEntry(new ZipEntry(texName));
                                    zos.write(texBytes);
                                    zos.closeEntry();
                                }
                            } catch (Exception texEx) {
                                log.warn("Skipping texture download {}: {}", tUrl, texEx.getMessage());
                            }
                        }
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    private static boolean isZipBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }
}
