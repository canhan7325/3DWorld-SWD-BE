package com.project.w_3d_world.features.ai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ai_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_request_id")
    private Integer aiRequestId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "model_id", nullable = false)
    private Integer modelId;

    @Column(name = "base_version_id")
    private Integer baseVersionId;

    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType = "GENERATE";

    @Column(name = "prompt", columnDefinition = "text")
    private String prompt;

    @Column(name = "input_image_storage_key", columnDefinition = "text")
    private String inputImageStorageKey;

    @Setter
    @Column(name = "provider_name", length = 255)
    private String providerName;

    @Setter
    @Column(name = "provider_job_reference", length = 255)
    private String providerJobReference;

    @Column(name = "credits_used", nullable = false)
    private int creditsUsed = 0;

    @Setter
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Setter
    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Setter
    @Column(name = "completed_at")
    private Instant completedAt;

    public AiRequest(
            Integer userId,
            Integer modelId,
            Integer baseVersionId,
            String requestType,
            String actionType,
            String prompt,
            String inputImageStorageKey,
            String providerName,
            String providerJobReference,
            int creditsUsed,
            String status
    ) {
        this.userId = userId;
        this.modelId = modelId;
        this.baseVersionId = baseVersionId;
        this.requestType = requestType;
        this.actionType = actionType == null ? "GENERATE" : actionType;
        this.prompt = prompt;
        this.inputImageStorageKey = inputImageStorageKey;
        this.providerName = providerName;
        this.providerJobReference = providerJobReference;
        this.creditsUsed = Math.max(0, creditsUsed);
        this.status = status == null ? "PENDING" : status;
        this.createdAt = Instant.now();
    }

    public void markProcessing(String providerJobReference) {
        this.providerJobReference = providerJobReference;
        this.status = "PROCESSING";
    }

    public void markSuccess() {
        this.status = "SUCCESS";
        this.failureReason = null;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }
}
