package com.project.w_3d_world.features.model.storage;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.project.w_3d_world.features.model.storage.ModelStorageException.Code.*;

public final class ModelStorageKeyFactory {
    private static final Set<String> EXTENSIONS = Set.of("glb", "stl", "obj", "3mf", "json", "png", "jpg", "jpeg");
    private static final Pattern KEY = Pattern.compile(
            "models/([1-9][0-9]{0,9})/versions/([1-9][0-9]{0,9})/files/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(glb|stl|obj|3mf|json|png|jpg|jpeg)");
    private final Supplier<UUID> uuidSupplier;

    public ModelStorageKeyFactory() {
        this(UUID::randomUUID);
    }

    /** Allows deterministic collision tests without weakening production UUID generation. */
    public ModelStorageKeyFactory(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier);
    }

    public String create(Integer modelId, Integer versionId, String extension) {
        if (modelId == null || modelId <= 0 || versionId == null || versionId <= 0
                || extension == null || !EXTENSIONS.contains(extension)) {
            throw new ModelStorageException(INVALID_METADATA, "Positive model/version IDs and a supported format are required.");
        }
        return "models/" + modelId + "/versions/" + versionId + "/files/"
                + Objects.requireNonNull(uuidSupplier.get()) + "." + extension;
    }

    public void validate(String key) {
        var match = key == null ? null : KEY.matcher(key);
        if (match == null || !match.matches()) {
            throw new ModelStorageException(INVALID_KEY, "Invalid managed storage key.");
        }
        try {
            Integer.parseInt(match.group(1));
            Integer.parseInt(match.group(2));
        } catch (NumberFormatException exception) {
            throw new ModelStorageException(INVALID_KEY, "Invalid managed storage key.");
        }
    }
}
