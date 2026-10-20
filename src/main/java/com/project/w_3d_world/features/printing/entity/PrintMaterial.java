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
@Table(name = "print_materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_id")
    private Integer materialId;

    @Column(name = "material_name", nullable = false, length = 255)
    private String materialName;

    @Column(name = "technology_code", nullable = false, length = 30)
    private String technologyCode;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "estimated_unit_cost", precision = 18, scale = 2)
    private BigDecimal estimatedUnitCost;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    public PrintMaterial(
            String materialName,
            String technologyCode,
            String unit,
            BigDecimal estimatedUnitCost,
            Boolean isActive) {
        this.materialName = materialName;
        this.technologyCode = technologyCode;
        this.unit = unit;
        this.estimatedUnitCost = estimatedUnitCost;
        this.isActive = isActive == null ? Boolean.TRUE : isActive;
    }

    public PrintMaterial(
            String materialName,
            String technologyCode,
            String unit,
            BigDecimal estimatedUnitCost) {
        this(materialName, technologyCode, unit, estimatedUnitCost, Boolean.TRUE);
    }
}
