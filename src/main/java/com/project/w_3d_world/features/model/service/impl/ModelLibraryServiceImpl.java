package com.project.w_3d_world.features.model.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.model.dto.response.LatestModelVersionResponse;
import com.project.w_3d_world.features.model.dto.response.ModelDetailResponse;
import com.project.w_3d_world.features.model.dto.response.ModelSummaryResponse;
import com.project.w_3d_world.features.model.dto.response.PagedModelResponse;
import com.project.w_3d_world.features.model.entity.Model3D;
import com.project.w_3d_world.features.model.entity.ModelVersion;
import com.project.w_3d_world.features.model.repository.Model3DRepository;
import com.project.w_3d_world.features.model.repository.ModelVersionRepository;
import com.project.w_3d_world.features.model.service.ModelLibraryService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModelLibraryServiceImpl implements ModelLibraryService {
    private static final int MAX_PAGE_SIZE = 50;

    private final Model3DRepository modelRepository;
    private final ModelVersionRepository versionRepository;

    @Override
    public PagedModelResponse getMyModels(Integer authenticatedUserId, int page, int size) {
        validateUser(authenticatedUserId);
        validatePage(page, size);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "modelId"));
        var models = modelRepository.findByOwnerUserIdAndDeletedAtIsNull(
                authenticatedUserId, PageRequest.of(page, size, sort));
        return new PagedModelResponse(
                models.getContent().stream().map(ModelLibraryServiceImpl::summary).toList(),
                models.getNumber(), models.getSize(), models.getTotalElements(), models.getTotalPages());
    }

    @Override
    public ModelDetailResponse getModelDetails(Integer authenticatedUserId, Integer modelId) {
        validateUser(authenticatedUserId);
        if (modelId == null || modelId <= 0) {
            throw ApiException.invalidRequest("Model ID must be a positive integer.");
        }
        Model3D model = modelRepository
                .findByModelIdAndOwnerUserIdAndDeletedAtIsNull(modelId, authenticatedUserId)
                .orElseThrow(() -> new ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Model was not found."));
        ModelVersion latest = versionRepository
                .findByModelIdOrderByVersionNumberDesc(modelId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        LatestModelVersionResponse latestResponse = latest == null ? null : new LatestModelVersionResponse(
                latest.getModelVersionId(), latest.getVersionNumber(), latest.getSourceType());
        return new ModelDetailResponse(model.getModelId(), model.getModelName(), model.getDescription(),
                model.getModelType(), model.getOriginType(), model.getVisibility(), latestResponse,
                model.getCreatedAt(), model.getUpdatedAt());
    }

    private static ModelSummaryResponse summary(Model3D model) {
        return new ModelSummaryResponse(model.getModelId(), model.getModelName(), model.getDescription(),
                model.getModelType(), model.getOriginType(), model.getVisibility(), model.getCreatedAt());
    }

    private static void validateUser(Integer userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.invalidRequest("Authenticated user ID is invalid.");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw ApiException.invalidRequest("Page must be greater than or equal to 0.");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.invalidRequest("Page size must be between 1 and 50.");
        }
    }
}
