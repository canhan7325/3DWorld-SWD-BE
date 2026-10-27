package com.project.w_3d_world.features.ai.provider;

import com.project.w_3d_world.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Fallback provider adapter used in production when MESHY_API_KEY is not configured
 * and mock provider is not explicitly enabled. Fails fast with 503 Service Unavailable.
 */
public class UnconfiguredAdapter implements ProviderAdapter {

    @Override
    public String name() {
        return "UNCONFIGURED";
    }

    @Override
    public String submitText(String prompt, Map<String, Object> config) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI generation provider is not configured. MESHY_API_KEY is required in production.");
    }

    @Override
    public String submitImage(byte[] imageBytes, String mimeType, Map<String, Object> config) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI generation provider is not configured. MESHY_API_KEY is required in production.");
    }

    @Override
    public ProviderTask getTask(String externalTaskId, String taskType) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI generation provider is not configured.");
    }

    @Override
    public void cancel(String externalTaskId, String taskType) {
        // no-op
    }

    @Override
    public byte[] downloadGlb(String glbUrl) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI generation provider is not configured.");
    }
}
