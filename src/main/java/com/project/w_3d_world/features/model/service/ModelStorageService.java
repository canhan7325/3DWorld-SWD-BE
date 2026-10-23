package com.project.w_3d_world.features.model.service;

import com.project.w_3d_world.features.model.enums.ModelFileRole;
import com.project.w_3d_world.features.model.storage.StoredModelFile;

import java.io.InputStream;

/** Private storage only. The caller must authorize model/version ownership before every operation. */
public interface ModelStorageService {
    /** Reads from the current position. The caller retains ownership of the input stream. */
    StoredModelFile store(InputStream input, String originalFilename, String declaredContentType,
            ModelFileRole role, Integer modelId, Integer modelVersionId);

    /** The caller must close the returned read-only stream. */
    InputStream open(String storageKey);

    /** Returns false if absent, true if deleted; actual I/O failures are reported. Never recursive. */
    boolean deleteForCleanup(String storageKey);
}
