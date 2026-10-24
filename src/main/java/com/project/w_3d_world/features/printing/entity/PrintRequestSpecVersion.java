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
@Table(name = "print_request_spec_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequestSpecVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spec_version_id")
    private Integer specVersionId;

    @Column(name = "print_request_id", nullable = false)
    private Integer printRequestId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specification_data", nullable = false, columnDefinition = "jsonb")
    private String specificationData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by_user_id", nullable = false)
    private Integer createdByUserId;

    public PrintRequestSpecVersion(Integer printRequestId, Integer version,
            String specificationData, Integer createdByUserId) {
        this.printRequestId = printRequestId;
        this.version = version;
        this.specificationData = specificationData;
        this.createdByUserId = createdByUserId;
    }
}
