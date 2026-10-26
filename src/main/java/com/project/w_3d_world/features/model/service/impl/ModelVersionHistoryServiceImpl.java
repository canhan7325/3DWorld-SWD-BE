package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.features.model.dto.response.ModelFileInfoResponse;
import com.project.w_3d_world.features.model.dto.response.ModelVersionDetailResponse;
import com.project.w_3d_world.features.model.dto.response.ModelVersionSummaryResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelVersionsResponse;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.exception.ModelErrors;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelVersionHistoryService;
import com.project.w_3d_world.features.model.validation.ModelServiceValidation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModelVersionHistoryServiceImpl implements ModelVersionHistoryService {
    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;

    @Override
    public PagedModelVersionsResponse getModelVersions(
            Integer authenticatedUserId, Integer modelId, int page, int size) {
        ModelServiceValidation.validateUser(authenticatedUserId);
        ModelServiceValidation.validatePositive(modelId, "Model ID");
        ModelServiceValidation.validatePage(page, size);
        requireOwnedModel(authenticatedUserId, modelId);

        var versions = versionRepository.findByModelIdOrderByVersionNumberDesc(
                modelId, PageRequest.of(page, size));
        return new PagedModelVersionsResponse(
                versions.getContent().stream().map(ModelVersionHistoryServiceImpl::summary).toList(),
                versions.getNumber(), versions.getSize(), versions.getTotalElements(), versions.getTotalPages());
    }

    @Override
    public ModelVersionDetailResponse getModelVersionDetails(
            Integer authenticatedUserId, Integer modelId, Integer versionId) {
        ModelServiceValidation.validateUser(authenticatedUserId);
        ModelServiceValidation.validatePositive(modelId, "Model ID");
        ModelServiceValidation.validatePositive(versionId, "Version ID");
        requireOwnedModel(authenticatedUserId, modelId);

        ModelVersion version = versionRepository.findByModelVersionIdAndModelId(versionId, modelId)
                .orElseThrow(ModelErrors::modelOrVersionNotFound);
        var files = fileRepository.findByModelVersionIdOrderByFileIdAsc(versionId).stream()
                .map(file -> new ModelFileInfoResponse(file.getFileId(), file.getFileRole(),
                        file.getFileFormat(), file.getFileSize(), file.isPrimary()))
                .toList();
        return new ModelVersionDetailResponse(version.getModelVersionId(), version.getModelId(),
                version.getVersionNumber(), version.getSourceType(), version.getParentVersionId(),
                version.getChangeNote(), version.getCreatedAt(), files);
    }

    private void requireOwnedModel(Integer userId, Integer modelId) {
        modelRepository.findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, userId)
                .orElseThrow(ModelErrors::modelOrVersionNotFound);
    }

    private static ModelVersionSummaryResponse summary(ModelVersion version) {
        return new ModelVersionSummaryResponse(version.getModelVersionId(), version.getVersionNumber(),
                version.getSourceType(), version.getParentVersionId(), version.getChangeNote(), version.getCreatedAt());
    }

}
