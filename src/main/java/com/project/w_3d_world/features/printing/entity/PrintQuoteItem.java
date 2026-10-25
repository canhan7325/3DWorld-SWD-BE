package com.project.w_3d_world.features.printing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "print_quote_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintQuoteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_quote_item_id", nullable = false)
    private Integer printQuoteItemId;

    @Column(name = "print_quote_id", nullable = false)
    private Integer printQuoteId;

    @Column(name = "print_request_item_id", nullable = false)
    private Integer printRequestItemId;

    @Column(name = "file_version_snapshot")
    private Integer fileVersionSnapshot;

    @Column(name = "specification_version_snapshot")
    private Integer specificationVersionSnapshot;

    @Column(name = "file_reference_snapshot", nullable = false, columnDefinition = "text")
    private String fileReferenceSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specification_snapshot", nullable = false, columnDefinition = "jsonb")
    private String specificationSnapshot;

    @Column(name = "quantity_snapshot", nullable = false)
    private Integer quantitySnapshot;

    @Column(name = "material_cost_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal materialCostSnapshot;

    @Column(name = "labor_cost_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal laborCostSnapshot;

    @Column(name = "option_fee_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal optionFeeSnapshot;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    public PrintQuoteItem(Integer printQuoteId, Integer printRequestItemId,
            Integer fileVersionSnapshot, Integer specificationVersionSnapshot,
            String fileReferenceSnapshot, String specificationSnapshot,
            Integer quantitySnapshot, BigDecimal materialCostSnapshot,
            BigDecimal laborCostSnapshot, BigDecimal optionFeeSnapshot,
            BigDecimal lineTotal) {
        this.printQuoteId = printQuoteId;
        this.printRequestItemId = printRequestItemId;
        this.fileVersionSnapshot = fileVersionSnapshot;
        this.specificationVersionSnapshot = specificationVersionSnapshot;
        this.fileReferenceSnapshot = fileReferenceSnapshot;
        this.specificationSnapshot = specificationSnapshot;
        this.quantitySnapshot = quantitySnapshot;
        this.materialCostSnapshot = materialCostSnapshot.setScale(2, RoundingMode.UNNECESSARY);
        this.laborCostSnapshot = laborCostSnapshot.setScale(2, RoundingMode.UNNECESSARY);
        this.optionFeeSnapshot = optionFeeSnapshot.setScale(2, RoundingMode.UNNECESSARY);
        this.lineTotal = lineTotal.setScale(2, RoundingMode.UNNECESSARY);
    }
}
