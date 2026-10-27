package com.project.w_3d_world.features.ai.provider;

import java.util.Map;

/**
 * Common interface for AI 3D model generation providers.
 */
public interface ProviderAdapter {
    String name();

    /**
     * Submits a text-to-3d task.
     * @return provider task id
     */
    String submitText(String prompt, Map<String, Object> config);

    /**
     * Submits an image-to-3d task.
     * @return provider task id
     */
    String submitImage(byte[] imageBytes, String mimeType, Map<String, Object> config);

    /**
     * Retrieves current task status from provider.
     */
    ProviderTask getTask(String taskId, String requestType);

    /**
     * Downloads generated GLB model from the provider or internal buffer.
     */
    byte[] downloadGlb(String glbUrlOrReference);

    /**
     * Cancels an ongoing task if permitted.
     */
    void cancel(String taskId, String requestType);
}
