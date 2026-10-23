package com.project.w_3d_world.features.printing;

import com.project.w_3d_world.features.printing.validation.PrintMaterialCompatibilityPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrintMaterialCompatibilityPolicyTest {
    @Test
    void acceptsApprovedPairsAndCanonicalizesInput() {
        for (String name : new String[] {"PLA", "PETG", "ABS"}) {
            assertEquals(name, PrintMaterialCompatibilityPolicy.canonicalName(name.toLowerCase(), "FDM"));
            PrintMaterialCompatibilityPolicy.requireCanonicalMaterial("FDM", name, "GRAM");
        }
        assertEquals("FDM", PrintMaterialCompatibilityPolicy.canonicalTechnology(" fdm "));
        assertEquals("Standard Resin", PrintMaterialCompatibilityPolicy.canonicalName(" standard resin ", "SLA"));
        assertEquals("ML", PrintMaterialCompatibilityPolicy.canonicalUnit(" ml ", "SLA"));
        PrintMaterialCompatibilityPolicy.requireCanonicalMaterial("SLA", "Standard Resin", "ML");
    }

    @Test
    void rejectsUnsupportedPairsAndUnits() {
        assertThrows(IllegalArgumentException.class,
                () -> PrintMaterialCompatibilityPolicy.canonicalName("Standard Resin", "FDM"));
        for (String name : new String[] {"PLA", "PETG", "ABS"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PrintMaterialCompatibilityPolicy.canonicalName(name, "SLA"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> PrintMaterialCompatibilityPolicy.canonicalTechnology("DLP"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintMaterialCompatibilityPolicy.canonicalName("unknown", "FDM"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintMaterialCompatibilityPolicy.canonicalUnit("ML", "FDM"));
        assertThrows(IllegalArgumentException.class,
                () -> PrintMaterialCompatibilityPolicy.requireCanonicalMaterial("fdm", "PLA", "GRAM"));
    }
}
