package com.project.w_3d_world.features.model.dto.response;

import com.project.w_3d_world.features.model.enums.ModelSourceType;
import java.time.Instant;

public record ModelVersionSummaryResponse(
        Integer modelVersionId,
        Integer versionNumber,
        ModelSourceType sourceType,
        Integer parentVersionId,
        String changeNote,
        Instant createdAt) {}
