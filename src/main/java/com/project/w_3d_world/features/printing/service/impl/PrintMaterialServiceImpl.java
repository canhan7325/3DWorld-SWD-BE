package com.project.w_3d_world.features.printing.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.response.PagedPrintMaterialResponse;
import com.project.w_3d_world.features.printing.dto.response.PrintMaterialResponse;
import com.project.w_3d_world.features.printing.entity.PrintMaterial;
import com.project.w_3d_world.features.printing.repository.PrintMaterialRepository;
import com.project.w_3d_world.features.printing.service.PrintMaterialService;
import com.project.w_3d_world.features.printing.validation.PrintMaterialServiceValidation;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintMaterialServiceImpl implements PrintMaterialService {
    private static final Sort CATALOG_SORT = Sort.by(
            Sort.Order.asc("materialName"), Sort.Order.asc("materialId"));

    private final PrintMaterialRepository repository;

    @Override
    public PagedPrintMaterialResponse getActiveMaterials(String technologyCode, int page, int size) {
        PrintMaterialServiceValidation.validatePage(page, size);
        PrintMaterialServiceValidation.validateTechnologyFilter(technologyCode);

        var pageable = PageRequest.of(page, size, CATALOG_SORT);
        Page<PrintMaterial> materials = technologyCode == null
                ? repository.findByIsActiveTrue(pageable)
                : repository.findByIsActiveTrueAndTechnologyCode(technologyCode, pageable);
        return new PagedPrintMaterialResponse(
                materials.getContent().stream().map(PrintMaterialServiceImpl::toResponse).toList(),
                materials.getNumber(), materials.getSize(), materials.getTotalElements(), materials.getTotalPages());
    }

    @Override
    public PrintMaterialResponse getActiveMaterialById(Integer materialId) {
        PrintMaterialServiceValidation.validateMaterialId(materialId);
        PrintMaterial material = repository.findById(materialId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material was not found."));
        return toResponse(material);
    }

    private static PrintMaterialResponse toResponse(PrintMaterial material) {
        return new PrintMaterialResponse(material.getMaterialId(), material.getMaterialName(),
                material.getTechnologyCode(), material.getUnit());
    }
}
