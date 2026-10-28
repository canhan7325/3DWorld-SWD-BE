package com.project.w_3d_world.features.ai.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and factory for AI 3D model generation providers.
 */
@Component
public class ProviderRegistry {
    private final Map<String, ProviderAdapter> adapters = new ConcurrentHashMap<>();
    private final String defaultProviderName;

    @Autowired
    public ProviderRegistry(
            Environment environment,
            @Value("${app.ai.meshy-api-key:}") String meshyApiKey,
            @Value("${app.ai.meshy-base-url:https://api.meshy.ai}") String meshyBaseUrl,
            @Value("${app.ai.meshy-asset-hosts:assets.meshy.ai}") String meshyAssetHosts,
            @Value("${app.ai.mock-provider-enabled:false}") boolean mockEnabled
    ) {
        boolean isDevOrTest = environment != null && environment.acceptsProfiles(Profiles.of("test", "dev"));
        boolean allowMock = mockEnabled || isDevOrTest;

        if (meshyApiKey != null && !meshyApiKey.isBlank()) {
            MeshyAdapter meshy = new MeshyAdapter(meshyBaseUrl, meshyApiKey, meshyAssetHosts);
            register(meshy);
            this.defaultProviderName = meshy.name();
            if (allowMock) {
                register(new MockAdapter(true));
            }
        } else if (allowMock) {
            MockAdapter mock = new MockAdapter(true);
            register(mock);
            this.defaultProviderName = mock.name();
        } else {
            UnconfiguredAdapter unconfigured = new UnconfiguredAdapter();
            register(unconfigured);
            this.defaultProviderName = unconfigured.name();
        }
    }

    public ProviderRegistry(String meshyApiKey, String meshyBaseUrl, String meshyAssetHosts, boolean mockEnabled) {
        this(null, meshyApiKey, meshyBaseUrl, meshyAssetHosts, mockEnabled);
    }

    public void register(ProviderAdapter adapter) {
        adapters.put(adapter.name().toUpperCase(), adapter);
    }

    public ProviderAdapter get() {
        return get(defaultProviderName);
    }

    public ProviderAdapter get(String name) {
        if (name == null || name.isBlank()) {
            return adapters.get(defaultProviderName);
        }
        ProviderAdapter adapter = adapters.get(name.toUpperCase());
        if (adapter == null) {
            return adapters.get(defaultProviderName);
        }
        return adapter;
    }

    public String defaultProviderName() {
        return defaultProviderName;
    }
}
