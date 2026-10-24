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

@Entity
@Table(name = "print_request_file_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequestFileVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_version_id")
    private Integer fileVersionId;

    @Column(name = "print_request_id", nullable = false)
    private Integer printRequestId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "file_url", nullable = false, columnDefinition = "text")
    private String fileUrl;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Integer uploadedByUserId;

    public PrintRequestFileVersion(Integer printRequestId, Integer version, String fileName,
            Long fileSize, String fileType, String fileUrl, Integer uploadedByUserId) {
        this.printRequestId = printRequestId;
        this.version = version;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.fileUrl = fileUrl;
        this.uploadedByUserId = uploadedByUserId;
    }
}
