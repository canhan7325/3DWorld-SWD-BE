package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestItemRequest;
import com.project.w_3d_world.features.printing.dto.request.CreatePrintRequestRequest;
import com.project.w_3d_world.features.printing.validation.PrintRequestCreateValidation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintRequestSpecificationValidationTest {
    @Test
    void sharedSpecificationRulesAcceptOptionalFieldsAndBoundaryValues() {
        var item = new CreatePrintRequestItemRequest(12, 3, "Black", "STANDARD", 100,
                new BigDecimal("9999999999.99"), null, new BigDecimal("0.01"), 1, null);
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateSpecification(null, List.of(item)));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateInfill("FDM", 0));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateInfill("SLA", null));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validateSpecification(null,
                List.of(item, item(12, 4, 2, null, null, null, null))));
    }

    @Test
    void allConfigurationsMustReferToOneModelVersion() {
        var first = item(12, 3, 1, null, null, null, null);
        var second = item(13, 3, 1, null, null, null, null);
        ApiException error = assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validateSpecification(null, List.of(first, second)));
        assertEquals("All Print Request items must use the same Model Version.", error.getMessage());
    }

    @Test
    void sharedSpecificationRulesRejectMissingOrInvalidItems() {
        var valid = item(12, 3, 1, null, null, null, null);
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateSpecification(null, null));
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateSpecification(null, List.of()));
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateSpecification(
                null, java.util.Arrays.asList(valid, null)));
        for (var invalid : List.of(
                item(null, 3, 1, null, null, null, null),
                item(12, 0, 1, null, null, null, null),
                item(12, 3, 0, null, null, null, null),
                item(12, 3, 1, -1, null, null, null),
                item(12, 3, 1, 101, null, null, null),
                item(12, 3, 1, null, BigDecimal.ZERO, null, null),
                item(12, 3, 1, null, new BigDecimal("1.001"), null, null),
                item(12, 3, 1, null, new BigDecimal("10000000000.00"), null, null),
                item(12, 3, 1, null, null, "x".repeat(101), null),
                item(12, 3, 1, null, null, null, "x".repeat(41)))) {
            assertThrows(ApiException.class,
                    () -> PrintRequestCreateValidation.validateSpecification(null, List.of(invalid)));
        }
        assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validateSpecification("x".repeat(10_001), List.of(valid)));
        assertThrows(ApiException.class, () -> PrintRequestCreateValidation.validateInfill("SLA", 20));
    }

    @Test
    void createStillRequiresExplicitRightsAcceptance() {
        var items = List.of(item(12, 3, 1, null, null, null, null));
        assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validate(new CreatePrintRequestRequest(null, false, items)));
        assertThrows(ApiException.class,
                () -> PrintRequestCreateValidation.validate(new CreatePrintRequestRequest(null, null, items)));
        assertDoesNotThrow(() -> PrintRequestCreateValidation.validate(
                new CreatePrintRequestRequest(null, true, items)));
    }

    private static CreatePrintRequestItemRequest item(Integer versionId, Integer materialId,
            Integer quantity, Integer infill, BigDecimal width, String color, String quality) {
        return new CreatePrintRequestItemRequest(versionId, materialId, color, quality,
                infill, width, null, null, quantity, null);
    }
}
