package com.project.w_3d_world.features.printing.service;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.dto.response.ModelUploadResponse;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.service.ModelDownloadService;
import com.project.w_3d_world.features.model.service.ModelUploadService;
import com.project.w_3d_world.features.model.dto.request.CreateModelRequest;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestFileVersionResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PrintRequestFileVersionService {
    private static final Pattern MODEL_DOWNLOAD = Pattern.compile(
            "^/api/models/([1-9][0-9]*)/files/([1-9][0-9]*)/download$");

    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository items;
    private final PrintRequestFileVersionRepository fileVersions;
    private final ModelFileRepository modelFiles;
    private final ModelUploadService modelUpload;
    private final ModelDownloadService modelDownload;

    @Transactional
    public void recordInitial(Integer customerId, Integer requestId,
            ModelUploadResponse upload, String fileName) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        var request = ownedForUpdate(customerId, requestId);
        if (!"DRAFT".equals(request.getStatus()) || request.getCurrentFileVersion() != null) {
            throw conflict();
        }
        saveFileVersion(requestId, 1, customerId, upload, fileName);
        request.useFileVersion(1);
        requests.saveAndFlush(request);
    }

    @Transactional
    public PrintRequestFileVersionResponse correct(Integer customerId, Integer requestId,
            Integer expectedVersion, MultipartFile file) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (expectedVersion == null || expectedVersion <= 0) {
            throw ApiException.invalidRequest("expectedCurrentFileVersion must be positive.");
        }
        String name = ModelServiceValidation.modelNameFromFilename(
                file == null ? null : file.getOriginalFilename());
        if (file.isEmpty()) throw ApiException.invalidRequest("A non-empty file is required.");

        var request = ownedForUpdate(customerId, requestId);
        if (!"DRAFT".equals(request.getStatus()) || request.getLockedAt() != null
                || request.getSubmittedAt() != null || request.getCurrentQuoteId() != null
                || request.getCancelledAt() != null) throw conflict();
        if (!expectedVersion.equals(request.getCurrentFileVersion())) throw conflict();
        var current = fileVersions.findByPrintRequestIdAndVersion(requestId, expectedVersion)
                .orElseThrow(PrintRequestFileVersionService::conflict);
        if (current.getFileUrl() == null) throw conflict();

        var requestItems = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (requestItems.isEmpty() || requestItems.stream().anyMatch(item ->
                !"PENDING".equals(item.getProductionStatus()))) throw conflict();

        var uploaded = modelUpload.uploadModel(customerId, new CreateModelRequest(name, null), file);
        int next = Math.addExact(expectedVersion, 1);
        var saved = saveFileVersion(requestId, next, customerId, uploaded, file.getOriginalFilename());
        for (var item : requestItems) {
            item.replaceDraftModelVersion(uploaded.modelVersionId());
            items.saveAndFlush(item);
        }
        request.useFileVersion(next);
        requests.saveAndFlush(request);
        return response(saved, next);
    }

    @Transactional(readOnly = true)
    public List<PrintRequestFileVersionResponse> list(Integer customerId, Integer requestId) {
        var request = owned(customerId, requestId);
        return fileVersions.findByPrintRequestIdOrderByVersionDesc(requestId).stream()
                .map(version -> response(version, request.getCurrentFileVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AuthorizedModelDownload download(Integer customerId, Integer requestId, Integer version) {
        owned(customerId, requestId);
        if (version == null || version <= 0) {
            throw ApiException.invalidRequest("version must be positive.");
        }
        var record = fileVersions.findByPrintRequestIdAndVersion(requestId, version)
                .orElseThrow(PrintRequestFileVersionService::notFound);
        var matcher = MODEL_DOWNLOAD.matcher(record.getFileUrl());
        if (!matcher.matches()) throw notFound();
        try {
            return modelDownload.openAuthorizedDownload(customerId,
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException exception) {
            throw notFound();
        }
    }

    private PrintRequestFileVersion saveFileVersion(Integer requestId, Integer version,
            Integer customerId, ModelUploadResponse upload, String fileName) {
        if (upload == null || upload.fileId() == null || upload.modelId() == null
                || upload.modelVersionId() == null) throw new IllegalStateException("Model upload is incomplete.");
        var stored = modelFiles.findByFileIdAndModelVersionId(upload.fileId(), upload.modelVersionId())
                .orElseThrow(() -> new IllegalStateException("Uploaded Model file was not persisted."));
        if (!stored.isPrimary() || stored.getFileSize() == null || stored.getMimeType() == null
                || stored.getStorageKey() == null) {
            throw new IllegalStateException("Uploaded Model file metadata is incomplete.");
        }
        String protectedPath = "/api/models/" + upload.modelId() + "/files/"
                + upload.fileId() + "/download";
        return fileVersions.saveAndFlush(new PrintRequestFileVersion(requestId, version, fileName,
                stored.getFileSize(), stored.getMimeType(), protectedPath, customerId));
    }

    private PrintRequestFileVersionResponse response(PrintRequestFileVersion record, Integer current) {
        return new PrintRequestFileVersionResponse(record.getPrintRequestId(), record.getVersion(),
                record.getFileName(), record.getFileSize(), record.getFileType(),
                record.getUploadedAt(), record.getVersion().equals(current),
                "/api/print/requests/getId/" + record.getPrintRequestId()
                        + "/file/versions/" + record.getVersion() + "/download");
    }

    private PrintRequest owned(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        return requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintRequestFileVersionService::notFound);
    }

    private PrintRequest ownedForUpdate(Integer customerId, Integer requestId) {
        return requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintRequestFileVersionService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found.");
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "Print Request file cannot be changed in its current state.");
    }
}
