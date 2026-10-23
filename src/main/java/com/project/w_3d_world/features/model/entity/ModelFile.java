package com.project.w_3d_world.features.model.entity;

import com.project.w_3d_world.features.model.enums.ModelFileRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "model_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Integer fileId;

    @Column(name = "model_version_id", nullable = false)
    private Integer modelVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_role", nullable = false, length = 30)
    private ModelFileRole fileRole;

    @Column(name = "file_format", nullable = false, length = 30)
    private String fileFormat;

    @Column(name = "storage_key", nullable = false, columnDefinition = "text")
    private String storageKey;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_hash", length = 255)
    private String fileHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ModelFile(
            Integer modelVersionId,
            ModelFileRole fileRole,
            String fileFormat,
            String storageKey,
            String mimeType,
            String fileHash,
            Long fileSize,
            boolean isPrimary
    ) {
        this.modelVersionId = modelVersionId;
        this.fileRole = fileRole;
        this.fileFormat = fileFormat;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.isPrimary = isPrimary;
    }
}
