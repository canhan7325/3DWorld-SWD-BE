package com.project.w_3d_world.features.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "print_request_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_request_item_id")
    private Integer printRequestItemId;

    @Column(name = "print_request_id", nullable = false)
    private Integer printRequestId;

    @Column(name = "model_version_id")
    private Integer modelVersionId;

    @Column(name = "uploaded_file_key", columnDefinition = "text")
    private String uploadedFileKey;

    @Column(name = "material_id", nullable = false)
    private Integer materialId;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "quality", length = 40)
    private String quality;

    @Column(name = "infill_percent")
    private Integer infillPercent;

    @Column(name = "width_mm", precision = 12, scale = 2)
    private BigDecimal widthMm;

    @Column(name = "height_mm", precision = 12, scale = 2)
    private BigDecimal heightMm;

    @Column(name = "depth_mm", precision = 12, scale = 2)
    private BigDecimal depthMm;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "production_status", nullable = false, length = 30)
    private String productionStatus;
}
