package com.project.w_3d_world.features.ai.provider;

import java.util.List;
import java.util.Map;

/**
 * Normalized status of a 3D generation task from a provider.
 * status: PENDING | IN_PROGRESS | SUCCEEDED | FAILED | CANCELED
 */
public record ProviderTask(
        String status,
        int progress,
        Map<String, String> modelUrls,
        String thumbnailUrl,
        List<Map<String, String>> textureUrls,
        String errorMessage
) {
    public boolean isDone() {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status);
    }

    public boolean isSuccess() {
        return "SUCCEEDED".equals(status);
    }

    public String glbUrl() {
        return modelUrls == null ? null : modelUrls.get("glb");
    }
}
