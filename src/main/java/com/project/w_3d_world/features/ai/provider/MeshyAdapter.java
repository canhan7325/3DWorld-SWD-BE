package com.project.w_3d_world.features.ai.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Meshy AI Open API adapter (https://docs.meshy.ai) for Text-to-3D and Image-to-3D.
 */
public class MeshyAdapter implements ProviderAdapter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final Set<String> allowedAssetHosts;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MeshyAdapter(String baseUrl, String apiKey, String allowedHosts) {
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.meshy.ai" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        Set<String> hosts = new HashSet<>();
        if (allowedHosts != null && !allowedHosts.isBlank()) {
            for (String h : allowedHosts.split(",")) {
                if (!h.isBlank()) hosts.add(h.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (hosts.isEmpty()) hosts.add("assets.meshy.ai");
        this.allowedAssetHosts = Collections.unmodifiableSet(hosts);
    }

    @Override
    public String name() {
        return "MESHY";
    }

    private JsonNode call(String method, String path, Object body) {
        if (apiKey.isBlank()) {
            throw new ProviderException("PROVIDER_NOT_CONFIGURED", "Meshy API key is not configured.", 503, false);
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json");

            if ("POST".equalsIgnoreCase(method)) {
                b.header("Content-Type", "application/json")
                 .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                b.DELETE();
            } else {
                b.GET();
            }

            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            JsonNode tree = null;
            try {
                if (res.body() != null && !res.body().isBlank()) {
                    tree = MAPPER.readTree(res.body());
                }
            } catch (Exception ignored) {}

            if (code >= 200 && code < 300) {
                return tree != null ? tree : MAPPER.createObjectNode();
            }

            String msg = tree != null && tree.has("message") ? tree.get("message").asText() : "HTTP " + code;
            if (code == 401 || code == 403) {
                throw new ProviderException("PROVIDER_AUTH", "Invalid Meshy API key: " + msg, code, false);
            }
            if (code == 402) {
                throw new ProviderException("PROVIDER_NO_CREDITS", "Meshy account out of credits: " + msg, code, false);
            }
            if (code == 404) {
                throw new ProviderException("PROVIDER_NOT_FOUND", "Task not found in Meshy: " + msg, code, false);
            }
            if (code == 429) {
                throw new ProviderException("PROVIDER_RATE_LIMIT", "Meshy rate limit exceeded: " + msg, code, true);
            }
            throw new ProviderException("PROVIDER_ERROR", "Meshy error (" + code + "): " + msg, code, code >= 500);
        } catch (ProviderException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("PROVIDER_UNREACHABLE", "Meshy call interrupted.", 502, true);
        } catch (Exception e) {
            throw new ProviderException("PROVIDER_UNREACHABLE", "Cannot connect to Meshy: " + e.getMessage(), 502, true);
        }
    }

    @Override
    public String submitText(String prompt, Map<String, Object> config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "preview");
        body.put("prompt", prompt);
        if (config != null) {
            if (config.containsKey("aiModel")) body.put("ai_model", config.get("aiModel"));
            if (config.containsKey("topology")) body.put("topology", config.get("topology"));
            if (config.containsKey("targetPolycount")) body.put("target_polycount", config.get("targetPolycount"));
            if (config.containsKey("shouldRemesh")) body.put("should_remesh", config.get("shouldRemesh"));
        }
        JsonNode res = call("POST", "/openapi/v2/text-to-3d", body);
        JsonNode result = res.path("result");
        if (!result.isTextual() || result.asText().isBlank()) {
            throw new ProviderException("PROVIDER_MALFORMED_RESPONSE", "Meshy did not return a task id.");
        }
        return result.asText();
    }

    @Override
    public String submitImage(byte[] imageBytes, String mimeType, Map<String, Object> config) {
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image_url", dataUri);
        body.put("should_texture", true);
        body.put("enable_pbr", true);
        body.put("target_formats", List.of("glb"));
        if (config != null) {
            if (config.containsKey("aiModel")) body.put("ai_model", config.get("aiModel"));
            if (config.containsKey("geometryResolution")) body.put("geometry_resolution", config.get("geometryResolution"));
            if (config.containsKey("textureResolution")) body.put("texture_resolution", config.get("textureResolution"));
            if (config.containsKey("shouldRemesh")) body.put("should_remesh", config.get("shouldRemesh"));
            if (config.containsKey("topology")) body.put("topology", config.get("topology"));
            if (config.containsKey("targetPolycount")) body.put("target_polycount", config.get("targetPolycount"));
        }
        JsonNode res = call("POST", "/openapi/v1/image-to-3d", body);
        JsonNode result = res.path("result");
        if (!result.isTextual() || result.asText().isBlank()) {
            throw new ProviderException("PROVIDER_MALFORMED_RESPONSE", "Meshy did not return an image task id.");
        }
        return result.asText();
    }

    @Override
    public ProviderTask getTask(String taskId, String requestType) {
        String path = "IMAGE_TO_3D".equalsIgnoreCase(requestType)
                ? "/openapi/v1/image-to-3d/" + taskId
                : "/openapi/v2/text-to-3d/" + taskId;
        JsonNode res = call("GET", path, null);
        String status = res.path("status").asText("PENDING").toUpperCase(Locale.ROOT);
        int progress = res.path("progress").asInt(0);
        String error = res.path("task_error").path("message").asText(null);

        Map<String, String> modelUrls = new LinkedHashMap<>();
        JsonNode urls = res.path("model_urls");
        if (urls.isObject()) {
            urls.properties().forEach(entry -> {
                if (entry.getValue().isTextual()) {
                    modelUrls.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        String thumb = res.path("thumbnail_url").asText(null);
        return new ProviderTask(status, progress, modelUrls, thumb, null, error);
    }

    @Override
    public byte[] downloadGlb(String glbUrlOrReference) {
        if (glbUrlOrReference == null || glbUrlOrReference.isBlank()) {
            throw new ProviderException("INVALID_GLB_URL", "Provider did not provide a valid GLB url.");
        }
        URI uri = URI.create(glbUrlOrReference);
        String host = uri.getHost();
        if (host == null || !allowedAssetHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new ProviderException("UNTRUSTED_ASSET_HOST", "Asset host not in allowed hosts: " + host);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                throw new ProviderException("DOWNLOAD_FAILED", "Failed to download GLB, HTTP " + res.statusCode());
            }
            try (InputStream in = res.body(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > 50_000_000) {
                        throw new ProviderException("FILE_TOO_LARGE", "Generated GLB exceeds 50MB limit.");
                    }
                }
                return out.toByteArray();
            }
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("DOWNLOAD_ERROR", "Error downloading model from provider: " + e.getMessage());
        }
    }

    @Override
    public void cancel(String taskId, String requestType) {
        String path = "IMAGE_TO_3D".equalsIgnoreCase(requestType)
                ? "/openapi/v1/image-to-3d/" + taskId
                : "/openapi/v2/text-to-3d/" + taskId;
        try {
            call("DELETE", path, null);
        } catch (Exception ignored) {}
    }
}
