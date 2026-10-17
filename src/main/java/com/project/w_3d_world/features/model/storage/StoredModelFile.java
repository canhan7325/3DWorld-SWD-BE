package com.project.w_3d_world.features.model.storage;

/** Metadata for later persistence. Contains no local filesystem paths. */
public record StoredModelFile(
        String storageKey, String fileFormat, String mimeType, long fileSize, String fileHash) {}
