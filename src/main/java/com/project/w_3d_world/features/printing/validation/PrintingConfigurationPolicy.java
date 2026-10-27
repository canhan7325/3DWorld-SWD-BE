package com.project.w_3d_world.features.printing.validation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validation for candidate configuration values; persistence and publication remain separate. */
public final class PrintingConfigurationPolicy {
    private PrintingConfigurationPolicy() {}

    public static String requireUnit(String technologyCode, String unit) {
        String technology = PrintMaterialCompatibilityPolicy.canonicalTechnology(technologyCode);
        return PrintMaterialCompatibilityPolicy.canonicalUnit(unit, technology);
    }

    public static void requireColorForMaterial(int materialId, ColorOption color) {
        if (materialId <= 0 || color == null || color.materialId() != materialId
                || !color.active() || !color.available()) {
            throw new IllegalArgumentException("Color is unavailable for the selected material.");
        }
    }

    public static void requireQualityForMaterial(int materialId, String technologyCode,
            QualityOption quality) {
        requireUnit(technologyCode, "FDM".equals(technologyCode) ? "GRAM" : "ML");
        if (materialId <= 0 || quality == null || !quality.active()
                || !technologyCode.equals(quality.technologyCode())
                || !quality.materialIds().contains(materialId)) {
            throw new IllegalArgumentException("Quality is unavailable for the selected material.");
        }
    }

    public static void requireFdmOptions(String technologyCode, FdmSelection selection,
            FdmLimits limits) {
        if (!"FDM".equals(technologyCode) || selection == null || limits == null
                || selection.infillPercent() < limits.minInfillPercent()
                || selection.infillPercent() > limits.maxInfillPercent()
                || !limits.supportModes().contains(selection.supportMode())
                || !limits.wallPresets().contains(selection.wallPreset())
                || !limits.adhesionModes().contains(selection.adhesionMode())) {
            throw new IllegalArgumentException("FDM options are incompatible or outside approved limits.");
        }
    }

    public static void requireResinOptions(String technologyCode, ResinSelection selection,
            ResinLimits limits) {
        if (!"SLA".equals(technologyCode) || selection == null || limits == null
                || !limits.supportPolicies().contains(selection.supportPolicy())
                || (selection.hollow() && (!limits.allowHollow() || selection.wallThicknessMm() == null
                        || selection.wallThicknessMm().compareTo(limits.minWallThicknessMm()) < 0
                        || selection.wallThicknessMm().compareTo(limits.maxWallThicknessMm()) > 0
                        || (limits.drainageRequired() && !selection.drainage())))) {
            throw new IllegalArgumentException("Resin options are incompatible or outside approved limits.");
        }
        if (!selection.hollow() && (selection.wallThicknessMm() != null || selection.drainage())) {
            throw new IllegalArgumentException("Solid Resin does not use hollow wall or drainage options.");
        }
    }

    public static void requirePostProcessing(List<PostProcessOption> available,
            Set<String> selectedCodes, String technologyCode) {
        if (available == null || selectedCodes == null || technologyCode == null) {
            throw new IllegalArgumentException("Post-processing configuration is required.");
        }
        Set<String> eligible = new java.util.HashSet<>();
        for (PostProcessOption option : available) {
            if (option == null) throw new IllegalArgumentException("Invalid post-processing option.");
            if (option.active() && ("ALL".equals(option.technologyCode())
                    || technologyCode.equals(option.technologyCode()))) {
                eligible.add(option.code());
                if (option.mandatory() && !selectedCodes.contains(option.code())) {
                    throw new IllegalArgumentException("Mandatory post-processing cannot be removed.");
                }
            }
        }
        if (!eligible.containsAll(selectedCodes)) {
            throw new IllegalArgumentException("Selected post-processing is unavailable.");
        }
    }

    private static void nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be nonnegative.");
        }
    }

    public record ColorOption(int materialId, String code, String hexCode,
            boolean active, boolean available, BigDecimal surcharge, int displayOrder) {
        public ColorOption {
            if (materialId <= 0 || code == null || code.isBlank() || hexCode == null
                    || !hexCode.matches("#[0-9a-fA-F]{6}") || displayOrder < 0) {
                throw new IllegalArgumentException("Invalid material color configuration.");
            }
            nonNegative(surcharge, "color surcharge");
        }
    }

    public record QualityOption(String technologyCode, String code, BigDecimal layerHeightMm,
            BigDecimal additionalFee, boolean active, Set<Integer> materialIds) {
        public QualityOption {
            if (code == null || code.isBlank() || layerHeightMm == null
                    || layerHeightMm.signum() <= 0 || materialIds == null || materialIds.isEmpty()
                    || materialIds.stream().anyMatch(Objects::isNull)
                    || materialIds.stream().anyMatch(id -> id <= 0)) {
                throw new IllegalArgumentException("Invalid quality configuration.");
            }
            requireUnit(technologyCode, "FDM".equals(technologyCode) ? "GRAM" : "ML");
            nonNegative(additionalFee, "quality fee");
            materialIds = Set.copyOf(materialIds);
        }
    }

    public record FdmLimits(int minInfillPercent, int maxInfillPercent,
            Set<String> supportModes, Set<String> wallPresets, Set<String> adhesionModes) {
        public FdmLimits {
            if (minInfillPercent < 0 || maxInfillPercent > 100
                    || minInfillPercent > maxInfillPercent
                    || supportModes == null || supportModes.isEmpty()
                    || wallPresets == null || wallPresets.isEmpty()
                    || adhesionModes == null || adhesionModes.isEmpty()
                    || supportModes.stream().anyMatch(value -> value == null || value.isBlank())
                    || wallPresets.stream().anyMatch(value -> value == null || value.isBlank())
                    || adhesionModes.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("Invalid FDM configuration limits.");
            }
            supportModes = Set.copyOf(supportModes);
            wallPresets = Set.copyOf(wallPresets);
            adhesionModes = Set.copyOf(adhesionModes);
        }
    }

    public record FdmSelection(int infillPercent, String supportMode,
            String wallPreset, String adhesionMode) {}

    public record ResinLimits(boolean allowHollow, BigDecimal minWallThicknessMm,
            BigDecimal maxWallThicknessMm, boolean drainageRequired,
            Set<String> supportPolicies) {
        public ResinLimits {
            if (minWallThicknessMm == null || minWallThicknessMm.signum() <= 0
                    || maxWallThicknessMm == null
                    || maxWallThicknessMm.compareTo(minWallThicknessMm) < 0
                    || supportPolicies == null || supportPolicies.isEmpty()
                    || supportPolicies.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("Invalid Resin configuration limits.");
            }
            supportPolicies = Set.copyOf(supportPolicies);
        }
    }

    public record ResinSelection(boolean hollow, BigDecimal wallThicknessMm,
            boolean drainage, String supportPolicy) {}

    public record PostProcessOption(String code, String technologyCode,
            boolean active, boolean mandatory, BigDecimal fee) {
        public PostProcessOption {
            if (code == null || code.isBlank()
                    || (!"ALL".equals(technologyCode) && !"FDM".equals(technologyCode)
                            && !"SLA".equals(technologyCode))) {
                throw new IllegalArgumentException("Invalid post-processing configuration.");
            }
            nonNegative(fee, "post-processing fee");
        }
    }
}
