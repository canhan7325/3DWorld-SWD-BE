package com.project.w_3d_world.features.model.entity;

import com.project.w_3d_world.features.model.enums.ModelOriginType;
import com.project.w_3d_world.features.model.enums.ModelType;

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
@Table(name = "models_3d")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Model3D {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_id")
    private Integer modelId;

    @Column(name = "owner_user_id", nullable = false)
    private Integer ownerUserId;

    @Column(name = "model_name", nullable = false, length = 255)
    private String modelName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 30)
    private ModelType modelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, length = 30)
    private ModelOriginType originType;

    @Column(name = "visibility", nullable = false, length = 30)
    private String visibility = "PRIVATE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Model3D(
            Integer ownerUserId,
            String modelName,
            String description,
            ModelType modelType,
            ModelOriginType originType,
            String visibility
    ) {
        this.ownerUserId = ownerUserId;
        this.modelName = modelName;
        this.description = description;
        this.modelType = modelType;
        this.originType = originType;
        this.visibility = visibility == null ? "PRIVATE" : visibility;
    }
}
