package com.project.w_3d_world.features.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "print_request_technical_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequestTechnicalReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "technical_review_id")
    private Integer technicalReviewId;

    @Column(name = "print_request_id", nullable = false)
    private Integer printRequestId;

    @Column(name = "reviewer_user_id", nullable = false)
    private Integer reviewerUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_data", nullable = false, columnDefinition = "jsonb")
    private String reviewData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public PrintRequestTechnicalReview(Integer printRequestId, Integer reviewerUserId,
            String reviewData) {
        this.printRequestId = printRequestId;
        this.reviewerUserId = reviewerUserId;
        this.reviewData = reviewData;
    }
}
