package com.project.w_3d_world.features.printing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PrintPricingCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_VND = new BigDecimal("9999999999999999");

    public QuoteBreakdown calculate(CostPolicy policy, List<QuoteLineInput> inputs) {
        if (policy == null || inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("A pricing policy and at least one item are required.");
        }
        List<LineBreakdown> lines = new ArrayList<>(inputs.size());
        BigDecimal configurationAndMaterial = BigDecimal.ZERO;
        BigDecimal machineFee = BigDecimal.ZERO;
        BigDecimal laborFee = BigDecimal.ZERO;
        BigDecimal electricityFee = BigDecimal.ZERO;
        for (QuoteLineInput input : inputs) {
            if (input == null || input.snapshot() == null || input.slicer() == null
                    || input.quantity() <= 0 || !policy.technologyCode().equals(input.snapshot().technologyCode())) {
                throw new IllegalArgumentException("Each item needs a compatible snapshot and positive quantity.");
            }
            PriceSnapshot snapshot = input.snapshot();
            SlicerInput slicer = input.slicer();
            positive(slicer.materialValue(), "slicerMaterialValue");
            positive(slicer.printHours(), "estimatedPrintHours");
            nonNegative(slicer.laborHours(), "laborHours");

            BigDecimal knownOptions = money(snapshot.technologyBaseFee().add(snapshot.qualityFee())
                    .add(snapshot.colorSurcharge()).add(snapshot.postProcessingFees()));
            BigDecimal materialBase = money(slicer.materialValue().multiply(snapshot.materialUnitPrice()));
            BigDecimal materialWaste = percent(materialBase, snapshot.wasteRatePct());
            BigDecimal materialCost = money(materialBase.add(materialWaste));
            BigDecimal groupOne = money(knownOptions.add(materialCost));
            BigDecimal machine = money(slicer.printHours().multiply(policy.machineHourRate()));
            BigDecimal labor = money(slicer.laborHours().multiply(policy.laborHourRate()));
            BigDecimal electricity = policy.machineRateIncludesElectricity()
                    ? BigDecimal.ZERO : money(slicer.printHours().multiply(policy.electricityHourRate()));
            BigDecimal count = BigDecimal.valueOf(input.quantity());
            lines.add(new LineBreakdown(input.quantity(), money(knownOptions.multiply(count)),
                    money(materialBase.multiply(count)), money(materialWaste.multiply(count)),
                    money(materialCost.multiply(count)), money(groupOne.multiply(count)),
                    money(machine.multiply(count)), money(labor.multiply(count)),
                    money(electricity.multiply(count))));
            configurationAndMaterial = money(configurationAndMaterial.add(groupOne.multiply(count)));
            machineFee = money(machineFee.add(machine.multiply(count)));
            laborFee = money(laborFee.add(labor.multiply(count)));
            electricityFee = money(electricityFee.add(electricity.multiply(count)));
        }
        BigDecimal policyBase = money(configurationAndMaterial.add(machineFee)
                .add(laborFee).add(electricityFee));
        BigDecimal riskFee = percent(policyBase, policy.riskRatePct());
        BigDecimal marginFee = percent(money(policyBase.add(riskFee)), policy.marginRatePct());
        BigDecimal beforeMinimum = money(policyBase.add(riskFee).add(marginFee));
        BigDecimal subtotal = money(beforeMinimum.max(policy.minimumOrderFee()));
        BigDecimal minimumAdjustment = money(subtotal.subtract(beforeMinimum));
        BigDecimal taxAmount = percent(subtotal, policy.taxRatePct());
        BigDecimal total = money(subtotal.add(taxAmount));
        return new QuoteBreakdown(List.copyOf(lines), configurationAndMaterial,
                machineFee, laborFee, electricityFee, riskFee, marginFee,
                minimumAdjustment, subtotal, taxAmount,
                money(total.subtract(configurationAndMaterial)), total);
    }

    private static BigDecimal money(BigDecimal value) {
        BigDecimal rounded = value.setScale(0, RoundingMode.CEILING);
        if (rounded.signum() < 0 || rounded.compareTo(MAX_VND) > 0) {
            throw new IllegalArgumentException("Calculated VND amount exceeds NUMERIC(18,2).");
        }
        return rounded;
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return money(amount.multiply(rate).divide(HUNDRED));
    }

    private static void nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_VND) > 0) {
            throw new IllegalArgumentException(name + " must be nonnegative and within range.");
        }
    }

    private static void positive(BigDecimal value, String name) {
        nonNegative(value, name);
        if (value.signum() == 0) throw new IllegalArgumentException(name + " must be positive.");
    }

    private static void rate(BigDecimal value, String name) {
        nonNegative(value, name);
        if (value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 100.");
        }
    }

    public record CostPolicy(String technologyCode, BigDecimal machineHourRate,
            BigDecimal laborHourRate, BigDecimal electricityHourRate,
            boolean machineRateIncludesElectricity, BigDecimal riskRatePct,
            BigDecimal marginRatePct, BigDecimal minimumOrderFee, BigDecimal taxRatePct) {
        public CostPolicy {
            if (!"FDM".equals(technologyCode) && !"RESIN".equals(technologyCode)) {
                throw new IllegalArgumentException("Pricing technology must be FDM or RESIN.");
            }
            nonNegative(machineHourRate, "machineHourRate");
            nonNegative(laborHourRate, "laborHourRate");
            nonNegative(electricityHourRate, "electricityHourRate");
            nonNegative(minimumOrderFee, "minimumOrderFee");
            rate(riskRatePct, "riskRatePct");
            rate(marginRatePct, "marginRatePct");
            rate(taxRatePct, "taxRatePct");
        }
    }

    public record PriceSnapshot(String technologyCode, String materialUnit,
            BigDecimal materialUnitPrice, BigDecimal wasteRatePct,
            BigDecimal technologyBaseFee, BigDecimal qualityFee,
            BigDecimal colorSurcharge, BigDecimal postProcessingFees) {
        public PriceSnapshot {
            if (!("FDM".equals(technologyCode) && "GRAM".equals(materialUnit))
                    && !("RESIN".equals(technologyCode) && "ML".equals(materialUnit))) {
                throw new IllegalArgumentException("Material unit is incompatible with technology.");
            }
            nonNegative(materialUnitPrice, "materialUnitPrice");
            rate(wasteRatePct, "wasteRatePct");
            nonNegative(technologyBaseFee, "technologyBaseFee");
            nonNegative(qualityFee, "qualityFee");
            nonNegative(colorSurcharge, "colorSurcharge");
            nonNegative(postProcessingFees, "postProcessingFees");
        }
    }

    public record SlicerInput(BigDecimal materialValue, BigDecimal printHours,
            BigDecimal laborHours) {}

    public record QuoteLineInput(PriceSnapshot snapshot, SlicerInput slicer, int quantity) {}

    public record LineBreakdown(int quantity, BigDecimal knownOptionTotal,
            BigDecimal materialBase, BigDecimal materialWaste, BigDecimal materialCost,
            BigDecimal configurationAndMaterial, BigDecimal machineFee,
            BigDecimal laborFee, BigDecimal electricityFee) {}

    public record QuoteBreakdown(List<LineBreakdown> lines,
            BigDecimal configurationAndMaterial, BigDecimal machineFee,
            BigDecimal laborFee, BigDecimal electricityFee, BigDecimal riskFee,
            BigDecimal marginFee, BigDecimal minimumAdjustment, BigDecimal subtotal,
            BigDecimal taxAmount, BigDecimal printingService, BigDecimal totalPrice) {}
}
