package com.project.w_3d_world.features.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateAiUnifiedTaskRequest(
        String mode,
        @JsonAlias({"title"})
        @Size(max = 255, message = "Name cannot exceed 255 characters.")
        String name,
        @Size(max = 800, message = "Prompt cannot exceed 800 characters.")
        String prompt,
        Map<String, Object> config,
        Integer variants,
        @JsonAlias({"imageIds"})
        List<String> uploadIds,
        Integer projectId,
        Boolean consent,
        String priceVersion,
        Integer quotedCredits
) {
    public CreateAiUnifiedTaskRequest(String mode, String name, String prompt, Map<String, Object> config, Integer variants) {
        this(mode, name, prompt, config, variants, null, null, null, null, null);
    }
}
