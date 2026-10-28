package com.project.w_3d_world.features.ai.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.ai.dto.response.AiUploadResponse;
import com.project.w_3d_world.features.ai.entity.AiRequest;
import com.project.w_3d_world.features.ai.model.StagedUpload;
import com.project.w_3d_world.features.ai.repository.AiRequestRepository;
import com.project.w_3d_world.features.ai.service.AiUploadService;
import com.project.w_3d_world.features.ai.util.ImageProbe;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUploadServiceImpl implements AiUploadService {

    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20 MB
    private static final int MIN_IMAGE_SIDE = 64;
    private static final int MAX_IMAGE_SIDE = 4096;

    private final AuditLogRepository auditLogRepository;
    private final AiRequestRepository aiRequestRepository;
    private final ModelStorageService storageService;

    private final Map<String, StagedUpload> stagedUploads = new ConcurrentHashMap<>();

    @Override
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
        String storageKey = "staging/" + userId + "/" + uploadId + "." + ext;

        // Save into bounded staged uploads memory store
        stagedUploads.put(uploadId, new StagedUpload(uploadId, userId, originalName, mime, file.getSize(), bytes, Instant.now()));

        // Evict expired staged uploads (older than 2 hours or if map exceeds 500)
        if (stagedUploads.size() > 500) {
            Instant cutoff = Instant.now().minus(java.time.Duration.ofHours(2));
            stagedUploads.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
        }

        auditLogRepository.save(new AuditLog(
                userId, "AI_IMAGE_UPLOADED", "UPLOAD",
                userId, "Uploaded " + originalName + " (" + file.getSize() + " bytes)", null
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

    @Override
    @Transactional(readOnly = true)
    public List<AiUploadResponse> listUploads(int userId) {
        List<AiUploadResponse> result = new ArrayList<>();

        // 1. Staged uploads belonging to this authenticated user
        stagedUploads.values().stream()
                .filter(s -> s.userId() == userId)
                .sorted(Comparator.comparing(StagedUpload::createdAt).reversed())
                .forEach(s -> result.add(new AiUploadResponse(
                        s.uploadId(),
                        "staging/" + userId + "/" + s.uploadId(),
                        s.filename(),
                        s.fileSize(),
                        s.mimeType(),
                        "/api/ai-studio/uploads/" + s.uploadId() + "/file",
                        s.createdAt()
                )));

        // 2. Durable uploads attached to user's AI requests
        List<AiRequest> requests = aiRequestRepository.findByUserIdAndInputImageStorageKeyIsNotNullOrderByCreatedAtDesc(userId);
        for (AiRequest r : requests) {
            String key = r.getInputImageStorageKey();
            String filename = key.substring(key.lastIndexOf('/') + 1);
            String mime = filename.endsWith(".png") ? "image/png" : (filename.endsWith(".webp") ? "image/webp" : "image/jpeg");
            String uploadIdentifier = "req-" + r.getAiRequestId();
            result.add(new AiUploadResponse(
                    uploadIdentifier,
                    key,
                    filename,
                    null,
                    mime,
                    "/api/ai-studio/uploads/" + uploadIdentifier + "/file",
                    r.getCreatedAt()
            ));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizedModelDownload downloadUploadFile(int userId, String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Upload asset not found.");
        }

        // 1. Check staged uploads
        StagedUpload staged = stagedUploads.get(uploadId);
        if (staged != null) {
            if (staged.userId() != userId) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Access denied to upload asset.");
            }
            return new AuthorizedModelDownload(
                    new ByteArrayInputStream(staged.data()),
                    staged.filename(),
                    staged.mimeType(),
                    staged.fileSize()
            );
        }

        // 2. Check durable AI request uploads (support both "123" and "req-123")
        Integer requestId = null;
        String rawId = uploadId.startsWith("req-") ? uploadId.substring(4) : uploadId;
        try {
            requestId = Integer.parseInt(rawId);
        } catch (NumberFormatException ignored) {}

        if (requestId != null) {
            AiRequest req = aiRequestRepository.findByAiRequestIdAndUserId(requestId, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Upload not found or access denied."));
            if (req.getInputImageStorageKey() == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "No image file attached to this task.");
            }
            String key = req.getInputImageStorageKey();
            try {
                InputStream is = storageService.open(key);
                String mime = key.endsWith(".png") ? "image/png" : (key.endsWith(".webp") ? "image/webp" : "image/jpeg");
                String filename = key.substring(key.lastIndexOf('/') + 1);
                return new AuthorizedModelDownload(is, filename, mime, null);
            } catch (Exception e) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Underlying image asset not found in storage.");
            }
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "Upload asset not found.");
    }

    @Override
    public StagedUpload getStagedImage(String uploadId) {
        return stagedUploads.get(uploadId);
    }

    @Override
    public void stageImage(String uploadId, StagedUpload upload) {
        stagedUploads.put(uploadId, upload);
    }

    @Override
    public void evictStagedImage(String uploadId) {
        stagedUploads.remove(uploadId);
    }
}
