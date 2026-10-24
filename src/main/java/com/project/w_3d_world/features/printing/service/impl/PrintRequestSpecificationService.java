package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationItemRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintRequestSpecificationRequest;
import com.project.w_3d_world.features.printing.dto.response.PrintRequestSpecVersionResponse;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.entity.PrintRequestSpecVersion;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestItemRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestRepository;
import com.project.w_3d_world.features.printing.repository.PrintRequestSpecVersionRepository;
import com.project.w_3d_world.features.printing.validation.PrintMaterialCompatibilityPolicy;
import com.project.w_3d_world.features.printing.validation.PrintRequestCreateValidation;
import com.project.w_3d_world.features.printing.validation.PrintRequestValidation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PrintRequestSpecificationService {
    private final PrintRequestRepository requests;
    private final PrintRequestItemRepository items;
    private final PrintRequestSpecVersionRepository versions;
    private final PrintMaterialRepository materials;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordInitial(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        var request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (!"DRAFT".equals(request.getStatus()) || request.getCurrentSpecVersion() != null
                || request.getCurrentFileVersion() == null) throw PrintingErrors.specificationChangeConflict();
        var persisted = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (persisted.isEmpty()) throw PrintingErrors.specificationChangeConflict();
        versions.saveAndFlush(new PrintRequestSpecVersion(requestId, 1,
                snapshot(request, persisted), customerId));
        request.useSpecVersion(1);
        requests.saveAndFlush(request);
    }

    @Transactional
    public PrintRequestSpecVersionResponse update(Integer customerId, Integer requestId,
            UpdatePrintRequestSpecificationRequest input) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        if (input == null || input.expectedCurrentSpecVersion() == null
                || input.expectedCurrentSpecVersion() <= 0 || input.items() == null
                || input.items().isEmpty()) {
            throw ApiException.invalidRequest("Expected spec version and items are required.");
        }
        var request = requests.findOwnedByIdForUpdate(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        if (!"DRAFT".equals(request.getStatus()) || request.getCancelledAt() != null
                || request.getLockedAt() != null || request.getSubmittedAt() != null
                || request.getCurrentQuoteId() != null) throw PrintingErrors.specificationChangeConflict();
        Integer expected = input.expectedCurrentSpecVersion();
        if (!expected.equals(request.getCurrentSpecVersion())
                || versions.findByPrintRequestIdAndVersion(requestId, expected).isEmpty()) {
            throw PrintingErrors.specificationChangeConflict();
        }
        var persisted = items.findByPrintRequestIdOrderByPrintRequestItemIdAsc(requestId);
        if (persisted.isEmpty() || persisted.size() != input.items().size()
                || persisted.stream().anyMatch(item -> !"PENDING".equals(item.getProductionStatus()))) {
            throw PrintingErrors.specificationChangeConflict();
        }
        Map<Integer, UpdatePrintRequestSpecificationItemRequest> changes = new LinkedHashMap<>();
        for (var change : input.items()) {
            if (change == null || change.printRequestItemId() == null
                    || change.printRequestItemId() <= 0
                    || changes.putIfAbsent(change.printRequestItemId(), change) != null) {
                throw ApiException.invalidRequest("Each existing item must appear exactly once.");
            }
        }
        Set<Integer> existingIds = new HashSet<>();
        List<CreatePrintRequestItemRequest> proposed = new ArrayList<>();
        for (var item : persisted) {
            existingIds.add(item.getPrintRequestItemId());
            var change = changes.get(item.getPrintRequestItemId());
            if (change == null) throw ApiException.invalidRequest("Unknown or missing Print Request item.");
            proposed.add(new CreatePrintRequestItemRequest(item.getModelVersionId(),
                    change.materialId(), change.color(), change.quality(), change.infillPercent(),
                    change.widthMm(), change.heightMm(), change.depthMm(), change.quantity(),
                    change.notes()));
        }
        if (!existingIds.equals(changes.keySet())) {
            throw ApiException.invalidRequest("Unknown or missing Print Request item.");
        }
        PrintRequestCreateValidation.validateSpecification(request.getCustomerNotes(), proposed);
        for (var change : input.items()) {
            var material = materials.findByIdForUpdate(change.materialId())
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
            PrintRequestCreateValidation.validateInfill(material.getTechnologyCode(),
                    change.infillPercent());
        }
        for (var item : persisted) {
            var change = changes.get(item.getPrintRequestItemId());
            item.updateSpecification(change.materialId(), change.color(), change.quality(),
                    change.infillPercent(), change.widthMm(), change.heightMm(),
                    change.depthMm(), change.quantity(), change.notes());
            items.saveAndFlush(item);
        }
        int next = Math.addExact(expected, 1);
        var saved = versions.saveAndFlush(new PrintRequestSpecVersion(requestId, next,
                snapshot(request, persisted), customerId));
        request.useSpecVersion(next);
        requests.saveAndFlush(request);
        return response(saved, next);
    }

    @Transactional(readOnly = true)
    public List<PrintRequestSpecVersionResponse> history(Integer customerId, Integer requestId) {
        PrintRequestValidation.validateCustomerId(customerId);
        PrintRequestValidation.validatePrintRequestId(requestId);
        var request = requests.findByPrintRequestIdAndCustomerUserId(requestId, customerId)
                .orElseThrow(PrintingErrors::requestNotFound);
        return versions.findByPrintRequestIdOrderByVersionDesc(requestId).stream()
                .map(value -> response(value, request.getCurrentSpecVersion())).toList();
    }

    private String snapshot(PrintRequest request, List<PrintRequestItem> persisted) {
        var data = new LinkedHashMap<String, Object>();
        data.put("customerNotes", request.getCustomerNotes());
        data.put("items", persisted.stream().map(item -> {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("printRequestItemId", item.getPrintRequestItemId());
            entry.put("materialId", item.getMaterialId());
            entry.put("color", item.getColor());
            entry.put("quality", item.getQuality());
            entry.put("infillPercent", item.getInfillPercent());
            entry.put("widthMm", item.getWidthMm());
            entry.put("heightMm", item.getHeightMm());
            entry.put("depthMm", item.getDepthMm());
            entry.put("quantity", item.getQuantity());
            entry.put("notes", item.getNotes());
            return entry;
        }).toList());
        return objectMapper.writeValueAsString(data);
    }

    private PrintRequestSpecVersionResponse response(PrintRequestSpecVersion value, Integer current) {
        return new PrintRequestSpecVersionResponse(value.getSpecVersionId(), value.getVersion(),
                objectMapper.readTree(value.getSpecificationData()), value.getCreatedAt(),
                value.getCreatedByUserId(), value.getVersion().equals(current));
    }

}
