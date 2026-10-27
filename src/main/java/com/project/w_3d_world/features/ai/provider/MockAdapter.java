package com.project.w_3d_world.features.ai.provider;

import com.project.w_3d_world.features.ai.util.Procedural;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock provider adapter for integration tests and development without external API keys.
 * Generates procedural glTF GLBs.
 */
public class MockAdapter implements ProviderAdapter {
    private static final long QUEUE_MS = 1000;
    private static final long RUN_MS = 2000;
    private static final SecureRandom RND = new SecureRandom();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final boolean instantSuccess;

    public MockAdapter() {
        this(true);
    }

    public MockAdapter(boolean instantSuccess) {
        this.instantSuccess = instantSuccess;
    }

    @Override
    public String name() {
        return "MOCK";
    }

    private static long startedAt(String id) {
        try {
            return Long.parseLong(id.split("-")[2]);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    @Override
    public String submitText(String prompt, Map<String, Object> config) {
        byte[] b = new byte[4];
        RND.nextBytes(b);
        return "mock-text-" + System.currentTimeMillis() + "-" + HexFormat.of().formatHex(b);
    }

    @Override
    public String submitImage(byte[] imageBytes, String mimeType, Map<String, Object> config) {
        byte[] b = new byte[4];
        RND.nextBytes(b);
        return "mock-image-" + System.currentTimeMillis() + "-" + HexFormat.of().formatHex(b);
    }

    @Override
    public ProviderTask getTask(String taskId, String requestType) {
        if (taskId == null) {
            throw new ProviderException("PROVIDER_NOT_FOUND", "Task ID is required.");
        }
        if (cancelled.contains(taskId)) {
            return new ProviderTask("CANCELED", 0, Map.of(), null, null, null);
        }
        if (instantSuccess) {
            return new ProviderTask("SUCCEEDED", 100,
                    Map.of("glb", "mock://glb/" + taskId, "stl", "mock://stl/" + taskId),
                    null, null, null);
        }
        long el = System.currentTimeMillis() - startedAt(taskId);
        if (el < QUEUE_MS) {
            return new ProviderTask("PENDING", 0, Map.of(), null, null, null);
        }
        if (el < QUEUE_MS + RUN_MS) {
            int p = (int) Math.min(99, Math.round((el - QUEUE_MS) * 100.0 / RUN_MS));
            return new ProviderTask("IN_PROGRESS", p, Map.of(), null, null, null);
        }
        return new ProviderTask("SUCCEEDED", 100,
                Map.of("glb", "mock://glb/" + taskId, "stl", "mock://stl/" + taskId),
                null, null, null);
    }

    @Override
    public byte[] downloadGlb(String glbUrlOrReference) {
        String seed = glbUrlOrReference == null ? "mock-seed" : glbUrlOrReference;
        return Procedural.makeGlb(seed);
    }

    @Override
    public void cancel(String taskId, String requestType) {
        cancelled.add(taskId);
    }
}
