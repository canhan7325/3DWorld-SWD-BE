package com.project.w_3d_world.features.model.dto.response;

import com.project.w_3d_world.features.model.enums.ModelSourceType;
import java.time.Instant;
import java.util.List;

public record ModelVersionDetailResponse(
        Integer modelVersionId,
        Integer modelId,
        Integer versionNumber,
        ModelSourceType sourceType,
        Integer parentVersionId,
        String changeNote,
        Instant createdAt,
        List<ModelFileInfoResponse> files) {}
