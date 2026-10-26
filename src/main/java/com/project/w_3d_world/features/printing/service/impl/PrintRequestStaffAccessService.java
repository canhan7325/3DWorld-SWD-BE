package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.download.AuthorizedModelDownload;
import com.project.w_3d_world.features.model.entity.ModelFile;
import com.project.w_3d_world.features.model.exception.ModelStorageException;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelStorageService;
import com.project.w_3d_world.features.model.storage.ModelStorageKeyFactory;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestFileVersionResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestTechnicalReviewResponse;
import com.project.w_3d_world.features.printing.dto.response.StaffPrintRequestResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.repository.PrintRequestFileVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestTechnicalReviewRepository;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestStaffAccessService {
    private static final Pattern MODEL_DOWNLOAD = Pattern.compile(
            "^/api/models/([1-9][0-9]*)/files/([1-9][0-9]*)/download$");

    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository items;
    private final PrintRequestFileVersionRepository fileVersions;
    private final PrintRequestSpecVersionRepository specVersions;
    private final PrintRequestTechnicalReviewRepository reviews;
    private final ModelVersionRepository modelVersions;
    private final ModelFileRepository modelFiles;
    private final ModelStorageService storage;
    private final ObjectMapper json;

    public StaffPrintRequestResponse detail(Integer requestId) {
        PrintRequest request = reviewable(requestId);
        var file = currentFile(request);
        var spec = specVersions.findByPrintRequestIdAndVersion(requestId,
                request.getCurrentSpecVersion())
                .orElseThrow(() -> conflict("Current specification version is unavailable."));
        var itemResponses = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId)
                .stream().map(item -> new PrintRequestItemResponse(
                        item.getPrintRequestItemId(), item.getMaterialId(), item.getModelVersionId(),
                        item.getModelVersionId() == null ? "UPLOADED_FILE" : "MODEL_VERSION",
                        item.getColor(), item.getQuality(), item.getInfillPercent(), item.getWidthMm(),
                        item.getHeightMm(), item.getDepthMm(), item.getQuantity(), item.getNotes(),
                        item.getProductionStatus())).toList();
        var reviewResponses = reviews.findByPrintRequestIdOrderByCreatedAtDescTechnicalReviewIdDesc(requestId)
                .stream().map(review -> {
                    var data = json.readTree(review.getReviewData());
                    return new PrintRequestTechnicalReviewResponse(review.getTechnicalReviewId(),
                            data.path("outcome").asText(), data.path("reviewedFileVersion").asInt(),
                            data.path("reviewedSpecVersion").asInt(),
                            data.path("reason").isMissingNode() ? null : data.path("reason").asText(),
                            review.getReviewerUserId(), review.getCreatedAt());
                }).toList();
        return new StaffPrintRequestResponse(requestId, request.getStatus(), request.getSubmittedAt(),
                request.getCurrentFileVersion(), request.getCurrentSpecVersion(),
                new PrintRequestFileVersionResponse(requestId, file.getVersion(), file.getFileName(),
                        file.getFileSize(), file.getFileType(), file.getUploadedAt(), true,
                        "/api/staff/print/requests/" + requestId + "/file/download"),
                json.readTree(spec.getSpecificationData()), itemResponses, reviewResponses);
    }

    public AuthorizedModelDownload openCurrentFile(Integer requestId) {
        PrintRequest request = reviewable(requestId);
        PrintRequestFileVersion version = currentFile(request);
        var match = MODEL_DOWNLOAD.matcher(version.getFileUrl());
        if (!match.matches()) throw conflict("Current file reference is invalid.");
        int modelId;
        int fileId;
        try {
            modelId = Integer.parseInt(match.group(1));
            fileId = Integer.parseInt(match.group(2));
        } catch (NumberFormatException exception) {
            throw conflict("Current file reference is invalid.");
        }
        ModelFile modelFile = modelFiles.findById(fileId)
                .orElseThrow(() -> conflict("Current file is unavailable."));
        var modelVersion = modelVersions.findByModelVersionIdAndModelId(
                modelFile.getModelVersionId(), modelId)
                .orElseThrow(() -> conflict("Current file is unavailable."));
        var requestItems = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (modelVersion.getModelVersionId() == null || modelFile.getFileId() == null
                || !modelFile.getFileId().equals(fileId)
                || requestItems.isEmpty() || requestItems.stream().anyMatch(item ->
                !modelVersion.getModelVersionId().equals(item.getModelVersionId()))
                || !modelFile.isPrimary() || modelFile.getStorageKey() == null
                || modelFile.getFileSize() == null || modelFile.getMimeType() == null
                || !modelFile.getFileSize().equals(version.getFileSize())
                || !modelFile.getMimeType().equals(version.getFileType())
                || !modelFile.getStorageKey().startsWith("models/" + modelId
                        + "/versions/" + modelVersion.getModelVersionId() + "/")) {
            throw conflict("Current file is unavailable.");
        }
        try {
            new ModelStorageKeyFactory().validate(modelFile.getStorageKey());
        } catch (ModelStorageException exception) {
            throw conflict("Current file is unavailable.");
        }
        InputStream stream;
        try {
            stream = storage.open(modelFile.getStorageKey());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Current file is temporarily unavailable.");
        }
        String extension = modelFile.getFileFormat();
        if (extension == null || !extension.matches("glb|stl|obj|3mf")) {
            try { stream.close(); } catch (Exception ignored) { }
            throw conflict("Current file format is unavailable.");
        }
        String filename = "print-request-" + requestId + "-file-v" + version.getVersion()
                + "." + extension;
        return new AuthorizedModelDownload(stream, filename, version.getFileType(), version.getFileSize());
    }

    private PrintRequest reviewable(Integer requestId) {
        PrintRequestValidation.validatePrintRequestId(requestId);
        PrintRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        if (request.getSubmittedAt() == null || request.getCancelledAt() != null
                || request.getLockedAt() != null || request.getCurrentQuoteId() != null
                || !("SUBMITTED".equals(request.getStatus())
                    || "REQUIRES_REVISION".equals(request.getStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Print Request is not available for review.");
        }
        return request;
    }

    private PrintRequestFileVersion currentFile(PrintRequest request) {
        if (request.getCurrentFileVersion() == null || request.getCurrentSpecVersion() == null) {
            throw conflict("Current Print Request versions are unavailable.");
        }
        return fileVersions.findByPrintRequestIdAndVersion(request.getPrintRequestId(),
                request.getCurrentFileVersion())
                .orElseThrow(() -> conflict("Current file version is unavailable."));
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
