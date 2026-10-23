package com.project.w_3d_world.features.model.entity;

import com.project.w_3d_world.features.model.enums.ModelSourceType;

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
@Table(name = "model_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_version_id")
    private Integer modelVersionId;

    @Column(name = "model_id", nullable = false)
    private Integer modelId;

    @Column(name = "parent_version_id")
    private Integer parentVersionId;

    @Column(name = "ai_request_id")
    private Integer aiRequestId;

    @Column(name = "created_by_user_id", nullable = false)
    private Integer createdByUserId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ModelSourceType sourceType;

    @Column(name = "change_note", columnDefinition = "text")
    private String changeNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ModelVersion(
            Integer modelId,
            Integer parentVersionId,
            Integer aiRequestId,
            Integer createdByUserId,
            Integer versionNumber,
            ModelSourceType sourceType,
            String changeNote
    ) {
        this.modelId = modelId;
        this.parentVersionId = parentVersionId;
        this.aiRequestId = aiRequestId;
        this.createdByUserId = createdByUserId;
        this.versionNumber = versionNumber;
        this.sourceType = sourceType;
        this.changeNote = changeNote;
    }
}
