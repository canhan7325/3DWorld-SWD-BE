package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.response.AdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PagedAdminPrintMaterialResponse;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.AdminPrintMaterialService;

import lombok.RequiredArgsConstructor;
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

    @Override
    public PagedAdminPrintMaterialResponse getMaterials(
            String technologyCode, Boolean isActive, int page, int size) {
        validate(technologyCode, page, size);
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

    private static void validate(String technologyCode, int page, int size) {
        if (technologyCode != null && (technologyCode.isBlank() || technologyCode.length() > 30
                || !technologyCode.equals(technologyCode.strip()))) {
            throw ApiException.invalidRequest("Technology code must have 1 to 30 characters without surrounding whitespace.");
        }
        if (page < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (size < 1 || size > 50) throw ApiException.invalidRequest("Page size must be between 1 and 50.");
    }

    private static AdminPrintMaterialResponse toResponse(PrintMaterial material) {
        return new AdminPrintMaterialResponse(material.getMaterialId(), material.getMaterialName(),
                material.getTechnologyCode(), material.getUnit(), material.getEstimatedUnitCost(), material.getIsActive());
    }
}
