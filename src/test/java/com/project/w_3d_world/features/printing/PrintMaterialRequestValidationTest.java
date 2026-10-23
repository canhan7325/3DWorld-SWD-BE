package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.printing.validation.PrintMaterialRequestValidation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrintMaterialRequestValidationTest {
    @Test
    void catalogFiltersAndPaginationStayExact() {
        assertNull(PrintMaterialRequestValidation.validateTechnology(null));
        assertEquals("FDM", PrintMaterialRequestValidation.validateTechnology("FDM"));
        assertNull(PrintMaterialRequestValidation.parseActive(null));
        assertEquals(Boolean.TRUE, PrintMaterialRequestValidation.parseActive("true"));
        assertEquals(Boolean.FALSE, PrintMaterialRequestValidation.parseActive("false"));
        assertEquals(0, PrintMaterialRequestValidation.parsePage("0"));
        assertEquals(50, PrintMaterialRequestValidation.parsePageSize("50"));
        for (String value : new String[] {"", " ", " FDM", "FDM ", "x".repeat(31)}) {
            assertEquals(400, assertThrows(ApiException.class,
                    () -> PrintMaterialRequestValidation.validateTechnology(value)).getStatus().value());
        }
        for (String value : new String[] {"", "TRUE", "unknown"}) {
            assertEquals(400, assertThrows(ApiException.class,
                    () -> PrintMaterialRequestValidation.parseActive(value)).getStatus().value());
        }
        assertEquals(400, assertThrows(ApiException.class,
                () -> PrintMaterialRequestValidation.parsePage("-1")).getStatus().value());
        assertEquals(400, assertThrows(ApiException.class,
                () -> PrintMaterialRequestValidation.parsePageSize("51")).getStatus().value());
    }

    @Test
    void actorAndMaterialIdValidationRemainFeatureScoped() {
        assertEquals(9, PrintMaterialRequestValidation.requireAdminId(
                new AuthenticatedUser(9, Set.of("ADMIN"), UUID.randomUUID())));
        assertEquals(401, assertThrows(ApiException.class,
                () -> PrintMaterialRequestValidation.requireAdminId(null)).getStatus().value());
        assertEquals(12, PrintMaterialRequestValidation.parseMaterialId("12"));
        assertEquals(400, assertThrows(ApiException.class,
                () -> PrintMaterialRequestValidation.parseMaterialId("0")).getStatus().value());
        assertEquals("materialId must be a valid integer.", assertThrows(ApiException.class,
                () -> PrintMaterialRequestValidation.parseMaterialId("bad")).getMessage());
        assertEquals("Material ID must be a positive integer.", assertThrows(ApiException.class,
                () -> PrintMaterialRequestValidation.parseCustomerMaterialId("0")).getMessage());
    }
}
