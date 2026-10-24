package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.exception.PrintingErrors;
import com.project.w_3d_world.features.audit.entity.AuditLog;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintMaterialRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialCostRequest;
import com.project.w_3d_world.features.printing.dto.request.UpdatePrintMaterialStatusRequest;
import com.project.w_3d_world.features.printing.dto.response.AdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.AdminPrintMaterialService;
import com.project.w_3d_world.features.printing.validation.PrintMaterialServiceValidation;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPrintMaterialServiceImpl implements AdminPrintMaterialService {
    private static final Sort ADMIN_SORT = Sort.by(
            Sort.Order.asc("materialName"), Sort.Order.asc("materialId"));
    private final PrintMaterialRepository repository;
    private final AuditLogRepository auditLogs;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    // Reserved two-integer PostgreSQL advisory key for active Print Material identity writes.
    private static final int MATERIAL_LOCK_CLASS = 0x3344574c;
    private static final int MATERIAL_LOCK_OBJECT = 0x504d4154;
    @Override
    public PagedAdminPrintMaterialResponse getMaterials(
            String technologyCode, Boolean isActive, int page, int size) {
        PrintMaterialServiceValidation.validateTechnologyFilter(technologyCode);
        PrintMaterialServiceValidation.validatePage(page, size);
        var pageable = PageRequest.of(page, size, ADMIN_SORT);
        Page<PrintMaterial> materials;
        if (technologyCode == null && isActive == null) {
            materials = repository.findAll(pageable);
        } else if (technologyCode != null && isActive == null) {
            materials = repository.findByTechnologyCode(technologyCode, pageable);
        } else if (technologyCode == null) {
            materials = repository.findByIsActive(isActive, pageable);
        } else {
            materials = repository.findByTechnologyCodeAndIsActive(technologyCode, isActive, pageable);
        }
        return new PagedAdminPrintMaterialResponse(
                materials.getContent().stream().map(AdminPrintMaterialServiceImpl::toResponse).toList(),
                materials.getNumber(), materials.getSize(), materials.getTotalElements(), materials.getTotalPages());
    }

    private static AdminPrintMaterialResponse toResponse(PrintMaterial material) {
        return new AdminPrintMaterialResponse(material.getMaterialId(), material.getMaterialName(),
                material.getTechnologyCode(), material.getUnit(), material.getEstimatedUnitCost(), material.getIsActive());
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED)
    public AdminPrintMaterialResponse create(int actorUserId, CreatePrintMaterialRequest request) {
        PrintMaterialServiceValidation.validateActor(actorUserId);
        PrintMaterialServiceValidation.validateCreateRequest(request);
        String technology = PrintMaterialServiceValidation.canonicalTechnology(request.technologyCode());
        String name = PrintMaterialServiceValidation.canonicalName(request.materialName(), technology);
        String unit = PrintMaterialServiceValidation.canonicalUnit(request.unit(), technology);
        PrintMaterialServiceValidation.validateCost(request.estimatedUnitCost());

        lockActiveIdentityNamespace();
        if (repository.existsActiveIdentity(PrintMaterialServiceValidation.normalizedName(name), technology)) throw PrintingErrors.duplicateActiveMaterial();
        PrintMaterial material = repository.saveAndFlush(
                new PrintMaterial(name, technology, unit, request.estimatedUnitCost()));
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("materialName", name);
        created.put("technologyCode", technology);
        created.put("unit", unit);
        created.put("estimatedUnitCost", request.estimatedUnitCost());
        created.put("isActive", true);
        audit(actorUserId, "MATERIAL_CREATED", material.getMaterialId(), null, created);
        return toResponse(material);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED)
    public AdminPrintMaterialResponse updateCost(
            int actorUserId, int materialId, UpdatePrintMaterialCostRequest request) {
        PrintMaterialServiceValidation.validateActor(actorUserId);
        PrintMaterialServiceValidation.validateMaterialId(materialId);
        PrintMaterialServiceValidation.validateCostRequest(request);
        PrintMaterialServiceValidation.validateCost(request.estimatedUnitCost());
        PrintMaterial material = lockedMaterial(materialId);
        BigDecimal oldCost = material.getEstimatedUnitCost();
        BigDecimal newCost = request.estimatedUnitCost();
        if (sameCost(oldCost, newCost)) return toResponse(material);
        material.changeEstimatedUnitCost(newCost);
        repository.saveAndFlush(material);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put("estimatedUnitCost", oldCost);
        after.put("estimatedUnitCost", newCost);
        audit(actorUserId, "MATERIAL_COST_UPDATED", materialId, before, after);
        return toResponse(material);
    }

    @Override
    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED)
    public AdminPrintMaterialResponse updateStatus(
            int actorUserId, int materialId, UpdatePrintMaterialStatusRequest request) {
        PrintMaterialServiceValidation.validateActor(actorUserId);
        PrintMaterialServiceValidation.validateMaterialId(materialId);
        PrintMaterialServiceValidation.validateStatusRequest(request);
        // All status writes use the same lock order: advisory namespace, then row.
        lockActiveIdentityNamespace();
        PrintMaterial material = lockedMaterial(materialId);
        boolean current = Boolean.TRUE.equals(material.getIsActive());
        if (current == request.isActive()) return toResponse(material);
        if (request.isActive()) {
            String technology = PrintMaterialServiceValidation.canonicalTechnology(material.getTechnologyCode());
            String name = PrintMaterialServiceValidation.canonicalName(material.getMaterialName(), technology);
            String unit = PrintMaterialServiceValidation.canonicalUnit(material.getUnit(), technology);
            PrintMaterialServiceValidation.validateLegacyIdentity(
                    material.getTechnologyCode(), material.getUnit(), technology, unit);
            if (repository.existsOtherActiveIdentity(materialId,
                    PrintMaterialServiceValidation.normalizedName(name), technology)) throw PrintingErrors.duplicateActiveMaterial();
            material.activate();
        } else material.deactivate();
        repository.saveAndFlush(material);
        audit(actorUserId, request.isActive() ? "MATERIAL_ACTIVATED" : "MATERIAL_DEACTIVATED",
                materialId, Map.of("isActive", current), Map.of("isActive", request.isActive()));
        return toResponse(material);
    }

    private void lockActiveIdentityNamespace() {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1, ?2)")
                .setParameter(1, MATERIAL_LOCK_CLASS)
                .setParameter(2, MATERIAL_LOCK_OBJECT)
                .getSingleResult();
    }

    private PrintMaterial lockedMaterial(int materialId) {
        return repository.findByIdForUpdate(materialId)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Material was not found."));
    }

    private void audit(int actorId, String action, int materialId,
            Map<String, ?> oldValue, Map<String, ?> newValue) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("oldValue", oldValue);
        metadata.put("newValue", newValue);
        auditLogs.saveAndFlush(new AuditLog(actorId, action, "PRINT_MATERIAL", materialId,
                null, objectMapper.writeValueAsString(metadata)));
    }

    private static boolean sameCost(BigDecimal oldCost, BigDecimal newCost) {
        return oldCost == null ? newCost == null : newCost != null && oldCost.compareTo(newCost) == 0;
    }

}
