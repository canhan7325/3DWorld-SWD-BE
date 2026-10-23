package com.project.w_3d_world.features.printing.validation;

import java.util.Locale;
import java.util.Map;

public final class PrintMaterialCompatibilityPolicy {
    private static final Map<String, Map<String, String>> CATALOG = Map.of(
            "FDM", Map.of("PLA", "PLA", "PETG", "PETG", "ABS", "ABS"),
            "SLA", Map.of("STANDARD RESIN", "Standard Resin"));
    private static final Map<String, String> UNITS = Map.of("FDM", "GRAM", "SLA", "ML");

    private PrintMaterialCompatibilityPolicy() {}

    public static String canonicalTechnology(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 30) {
            throw new IllegalArgumentException("Technology code is required and must not exceed 30 characters.");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!CATALOG.containsKey(code)) throw new IllegalArgumentException("Unsupported technology code.");
        return code;
    }

    public static String canonicalName(String raw, String technology) {
        if (raw == null || raw.isBlank() || raw.length() > 255) {
            throw new IllegalArgumentException("Material name is required and must not exceed 255 characters.");
        }
        Map<String, String> materials = CATALOG.get(technology);
        String canonical = materials == null ? null : materials.get(raw.trim().toUpperCase(Locale.ROOT));
        if (canonical == null) throw new IllegalArgumentException("Material is incompatible with the technology.");
        return canonical;
    }

    public static String canonicalUnit(String raw, String technology) {
        String approved = UNITS.get(technology);
        if (raw == null || raw.isBlank() || raw.length() > 30 || approved == null
                || !approved.equals(raw.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unit is incompatible with the technology.");
        }
        return approved;
    }

    public static void requireCanonicalMaterial(String technology, String name, String unit) {
        String canonicalTechnology = canonicalTechnology(technology);
        String canonicalName = canonicalName(name, canonicalTechnology);
        String canonicalUnit = canonicalUnit(unit, canonicalTechnology);
        if (!canonicalTechnology.equals(technology) || !canonicalName.equals(name)
                || !canonicalUnit.equals(unit)) {
            throw new IllegalArgumentException("Material identity is not canonical.");
        }
    }
}
