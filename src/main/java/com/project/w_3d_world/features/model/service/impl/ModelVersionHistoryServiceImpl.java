package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.response.ModelFileInfoResponse;
import com.project.w_3d_world.features.model.dto.response.ModelVersionDetailResponse;
import com.project.w_3d_world.features.model.dto.response.ModelVersionSummaryResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelVersionsResponse;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelFileRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelVersionHistoryService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModelVersionHistoryServiceImpl implements ModelVersionHistoryService {
    private static final int MAX_PAGE_SIZE = 50;
    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;
    private final ModelFileRepository fileRepository;

    @Override
    public PagedModelVersionsResponse getModelVersions(
            Integer authenticatedUserId, Integer modelId, int page, int size) {
        validateUser(authenticatedUserId);
        validatePositive(modelId, "Model ID");
        validatePage(page, size);
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
        validateUser(authenticatedUserId);
        validatePositive(modelId, "Model ID");
        validatePositive(versionId, "Version ID");
        requireOwnedModel(authenticatedUserId, modelId);

        ModelVersion version = versionRepository.findByModelVersionIdAndModelId(versionId, modelId)
                .orElseThrow(ModelVersionHistoryServiceImpl::notFound);
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
                .orElseThrow(ModelVersionHistoryServiceImpl::notFound);
    }

    private static ModelVersionSummaryResponse summary(ModelVersion version) {
        return new ModelVersionSummaryResponse(version.getModelVersionId(), version.getVersionNumber(),
                version.getSourceType(), version.getParentVersionId(), version.getChangeNote(), version.getCreatedAt());
    }

    private static void validateUser(Integer userId) {
        if (userId == null || userId <= 0) throw ApiException.invalidRequest("Authenticated user ID is invalid.");
    }

    private static void validatePositive(Integer value, String name) {
        if (value == null || value <= 0) throw ApiException.invalidRequest(name + " must be a positive integer.");
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (size < 1 || size > MAX_PAGE_SIZE) throw ApiException.invalidRequest("Page size must be between 1 and 50.");
    }

    private static ApiException notFound() {
        return new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Model or version was not found.");
    }
}
