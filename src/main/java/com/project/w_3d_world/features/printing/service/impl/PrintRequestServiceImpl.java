package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.storage.ModelFileValidator;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestItemResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintProductionSummaryResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSummaryResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestStatusHistory;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestStatusHistoryRepository;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.PrintRequestService;
import com.project.w_3d_world.features.printing.validation.PrintMaterialCompatibilityPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestCreateValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintRequestServiceImpl implements PrintRequestService {
    private static final Sort REQUEST_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("printRequestId"));

    private final PrintRequestRepository requestRepository;
    private final PrintRequestItemRepository itemRepository;
    private final PrintMaterialRepository materialRepository;
    private final ModelVersionRepository versionRepository;
    private final Model3DRepository modelRepository;
    private final ModelFileRepository fileRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final PrintRequestStatusHistoryRepository statusHistoryRepository;

    @Override
    @Transactional(readOnly = false)
    public PrintRequestResponse createDraft(Integer customerUserId, CreatePrintRequestRequest request) {
        PrintRequestValidation.validateCustomerId(customerUserId);
        PrintRequestCreateValidation.validate(request);

        // Validate every source and material before publishing the parent Request.
        for (CreatePrintRequestItemRequest item : request.items()) {
            requireOwnedModelFile(customerUserId, item.modelVersionId());
            var material = materialRepository.findByIdForUpdate(item.materialId())
                    .orElseThrow(() -> ApiException.invalidRequest("Material is unavailable."));
            if (!Boolean.TRUE.equals(material.getIsActive())) {
                throw ApiException.invalidRequest("Material is unavailable.");
            }
            try {
                PrintMaterialCompatibilityPolicy.requireCanonicalMaterial(
                        material.getTechnologyCode(), material.getMaterialName(), material.getUnit());
            } catch (IllegalArgumentException exception) {
                throw ApiException.invalidRequest("Material is incompatible with the approved catalog.");
            }
            PrintRequestCreateValidation.validateInfill(material.getTechnologyCode(), item.infillPercent());
        }

        PrintRequest draft = new PrintRequest(customerUserId, request.customerNotes());
        draft.confirmCopyrightDeclaration();
        PrintRequest savedRequest = requestRepository.saveAndFlush(draft);
        Integer requestId = savedRequest.getPrintRequestId();
        if (requestId == null) throw new IllegalStateException("Print Request ID was not generated.");
        statusHistoryRepository.saveAndFlush(new PrintRequestStatusHistory(
                requestId, null, "DRAFT", customerUserId, null, null));

        List<PrintRequestItemResponse> responses = new ArrayList<>();
        List<Integer> modelVersionIds = new ArrayList<>();
        for (CreatePrintRequestItemRequest item : request.items()) {
            var savedItem = itemRepository.saveAndFlush(new PrintRequestItem(
                    requestId, item.modelVersionId(), item.materialId(), item.color(), item.quality(),
                    item.infillPercent(), item.widthMm(), item.heightMm(), item.depthMm(),
                    item.quantity(), item.notes()));
            if (savedItem.getPrintRequestItemId() == null) {
                throw new IllegalStateException("Print Request item ID was not generated.");
            }
            responses.add(toItem(savedItem));
            modelVersionIds.add(item.modelVersionId());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("accepted", true);
        metadata.put("declarationVersion", "POD_RIGHTS_V1");
        metadata.put("modelVersionIds", modelVersionIds);
        metadata.put("acceptedAt", Instant.now().toString());
        auditLogRepository.saveAndFlush(new AuditLog(customerUserId, "POD_RIGHTS_DECLARED",
                "PRINT_REQUEST", requestId, null, objectMapper.writeValueAsString(metadata)));

        return new PrintRequestResponse(requestId, savedRequest.getStatus(),
                savedRequest.getCustomerNotes(), savedRequest.getCreatedAt(),
                savedRequest.getUpdatedAt(), savedRequest.getLockedAt(), List.copyOf(responses));
    }

    private void requireOwnedModelFile(Integer customerUserId, Integer modelVersionId) {
        var version = versionRepository.findById(modelVersionId)
                .orElseThrow(PrintingErrors::modelVersionNotFound);
        modelRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(
                version.getModelId(), customerUserId).orElseThrow(PrintingErrors::modelVersionNotFound);
        var file = fileRepository.findPrimaryByModelVersionId(modelVersionId)
                .orElseThrow(() -> ApiException.invalidRequest("Model Version has no eligible primary file."));
        if (file.getFileRole() == null || !java.util.Set.of(
                ModelFileRole.SOURCE_MODEL, ModelFileRole.VIEWABLE_MODEL, ModelFileRole.PRINT_MODEL)
                .contains(file.getFileRole()) || file.getStorageKey() == null
                || file.getStorageKey().isBlank() || file.getFileSize() == null
                || file.getFileSize() <= 0 || file.getFileSize() > ModelFileValidator.MODEL_LIMIT) {
            throw ApiException.invalidRequest("Model Version has no eligible primary file.");
        }
        try {
            new ModelFileValidator().validateMetadata("model." + file.getFileFormat(),
                    file.getMimeType(), file.getFileRole());
        } catch (RuntimeException exception) {
            throw ApiException.invalidRequest("Model Version has no eligible primary file.");
        }
    }

    @Override
    public PagedPrintRequestResponse getMyRequests(Integer customerUserId, int page, int size) {
        PrintRequestValidation.validateCustomerId(customerUserId);
        PrintRequestValidation.validatePage(page, size);
        var requests = requestRepository.findByCustomerUserId(
                customerUserId, PageRequest.of(page, size, REQUEST_SORT));
        return new PagedPrintRequestResponse(
                requests.getContent().stream().map(PrintRequestServiceImpl::toSummary).toList(),
                requests.getNumber(), requests.getSize(),
                requests.getTotalElements(), requests.getTotalPages());
    }

    @Override
    public PrintRequestResponse getMyRequestById(Integer customerUserId, Integer printRequestId) {
        PrintRequestValidation.validateCustomerId(customerUserId);
        PrintRequestValidation.validatePrintRequestId(printRequestId);
        PrintRequest request = requestRepository
                .findByPrintRequestIdAndCustomerUserId(printRequestId, customerUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Print Request was not found."));
        var items = itemRepository.findByPrintRequestIdOrderByPrintRequestItemIdAsc(printRequestId)
                .stream().map(PrintRequestServiceImpl::toItem).toList();
        return new PrintRequestResponse(request.getPrintRequestId(), request.getStatus(),
                request.getCustomerNotes(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getLockedAt(), items).withProduction(PrintProductionSummaryResponse
                        .fromStatuses(items.stream().map(PrintRequestItemResponse::productionStatus).toList()));
    }

    private static PrintRequestSummaryResponse toSummary(PrintRequest request) {
        return new PrintRequestSummaryResponse(request.getPrintRequestId(), request.getStatus(),
                request.getCustomerNotes(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getLockedAt());
    }

    private static PrintRequestItemResponse toItem(PrintRequestItem item) {
        String sourceType = item.getModelVersionId() == null ? "UPLOADED_FILE" : "MODEL_VERSION";
        return new PrintRequestItemResponse(item.getPrintRequestItemId(), item.getMaterialId(),
                item.getModelVersionId(), sourceType, item.getColor(), item.getQuality(),
                item.getInfillPercent(), item.getWidthMm(), item.getHeightMm(), item.getDepthMm(),
                item.getQuantity(), item.getNotes(), item.getProductionStatus());
    }
}
