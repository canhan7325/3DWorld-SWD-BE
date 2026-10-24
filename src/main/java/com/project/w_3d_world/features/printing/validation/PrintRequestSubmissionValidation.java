package com.project.w_3d_world.features.printing.validation;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.storage.ModelFileValidator;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequest;
import com.project.w_3d_world.features.printing.entity.PrintRequestFileVersion;
import com.project.w_3d_world.features.printing.entity.PrintRequestItem;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrintRequestSubmissionValidation {
    private static final Set<ModelFileRole> ELIGIBLE_ROLES = Set.of(
            ModelFileRole.SOURCE_MODEL, ModelFileRole.VIEWABLE_MODEL, ModelFileRole.PRINT_MODEL);

    private final ModelVersionRepository modelVersions;
    private final Model3DRepository models;
    private final ModelFileRepository modelFiles;
    private final PrintMaterialRepository materials;

    public void validate(PrintRequest request, PrintRequestFileVersion currentFile,
            List<PrintRequestItem> items) {
        if (items.isEmpty() || items.stream().anyMatch(item ->
                !"PENDING".equals(item.getProductionStatus()))) {
            throw conflict("Print Request items are unavailable for submission.");
        }
        var proposed = items.stream().map(item -> new CreatePrintRequestItemRequest(
                item.getModelVersionId(), item.getMaterialId(), item.getColor(), item.getQuality(),
                item.getInfillPercent(), item.getWidthMm(), item.getHeightMm(), item.getDepthMm(),
                item.getQuantity(), item.getNotes())).toList();
        PrintRequestCreateValidation.validateSpecification(request.getCustomerNotes(), proposed);

        Integer modelVersionId = items.getFirst().getModelVersionId();
        var modelVersion = modelVersions.findById(modelVersionId)
                .orElseThrow(() -> conflict("Print Request source is unavailable."));
        models.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(
                modelVersion.getModelId(), request.getCustomerUserId())
                .orElseThrow(() -> conflict("Print Request source is unavailable."));
        var primary = modelFiles.findPrimaryByModelVersionId(modelVersionId)
                .orElseThrow(() -> conflict("Print Request source is unavailable."));
        String expectedUrl = "/api/models/" + modelVersion.getModelId() + "/files/"
                + primary.getFileId() + "/download";
        if (!primary.isPrimary() || primary.getFileRole() == null
                || !ELIGIBLE_ROLES.contains(primary.getFileRole())
                || primary.getStorageKey() == null || primary.getStorageKey().isBlank()
                || primary.getFileSize() == null || primary.getFileSize() <= 0
                || primary.getFileSize() > ModelFileValidator.MODEL_LIMIT
                || primary.getMimeType() == null
                || !primary.getFileSize().equals(currentFile.getFileSize())
                || !primary.getMimeType().equals(currentFile.getFileType())
                || !expectedUrl.equals(currentFile.getFileUrl())) {
            throw conflict("Print Request source is unavailable.");
        }
        try {
            new ModelFileValidator().validateMetadata("model." + primary.getFileFormat(),
                    primary.getMimeType(), primary.getFileRole());
        } catch (RuntimeException exception) {
            throw conflict("Print Request source is unavailable.");
        }

        for (var item : items) {
            var material = materials.findByIdForUpdate(item.getMaterialId())
                    .orElseThrow(() -> conflict("Print Request material is unavailable."));
            if (!Boolean.TRUE.equals(material.getIsActive())) {
                throw conflict("Print Request material is unavailable.");
            }
            try {
                PrintMaterialCompatibilityPolicy.requireCanonicalMaterial(
                        material.getTechnologyCode(), material.getMaterialName(), material.getUnit());
                PrintRequestCreateValidation.validateInfill(
                        material.getTechnologyCode(), item.getInfillPercent());
            } catch (RuntimeException exception) {
                throw conflict("Print Request material is incompatible.");
            }
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
