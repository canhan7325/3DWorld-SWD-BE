package com.project.w_3d_world.features.shipping.packing;

import com.project.w_3d_world.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

/** Bounded first-fit decreasing / best-short-side-fit heuristic; floor rotation only, no stacking. */
@Service
public class BoxPackingService {
    public record Item(int cartItemId, int variantId, int quantity, int weightGrams, BigDecimal length,
                       BigDecimal width, BigDecimal height, boolean fragile, String group, BigDecimal price) {}
    public record Box(int id, String code, BigDecimal length, BigDecimal width, BigDecimal height,
                      int maxProductWeight, int boxWeight, int paddingWeight, boolean supportsFragile,
                      Set<String> groups, BigDecimal boxCost, BigDecimal paddingCost) {}
    public record Placement(int cartItemId, int variantId, BigDecimal x, BigDecimal y,
                            BigDecimal length, BigDecimal width, BigDecimal height, boolean rotated) {}
    public record Parcel(Box box, List<Placement> placements, int productWeight, int packagingWeight,
                         int totalWeight, BigDecimal declaredValue, boolean fragile, BigDecimal packagingCost) {}
    public record Plan(List<Parcel> parcels) {}
    private record Rect(BigDecimal x, BigDecimal y, BigDecimal length, BigDecimal width) {}
    private record Unit(Item item, BigDecimal length, BigDecimal width, BigDecimal height) {}
    private record Fit(int freeIndex, boolean rotated, BigDecimal length, BigDecimal width,
                       BigDecimal shortSide, BigDecimal longSide) {}

    public List<Plan> suggest(List<Item> items, List<Box> boxes, BigDecimal paddingCm) {
        if (paddingCm == null || paddingCm.signum() < 0 || paddingCm.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalStateException("Invalid packing padding configuration.");
        }
        long count = items.stream().mapToLong(Item::quantity).sum();
        if (items.isEmpty() || count < 1 || count > 100 || boxes.isEmpty() || boxes.size() > 50) {
            throw ApiException.invalidRequest("Quote supports 1-100 product units and 1-50 active packaging profiles.");
        }
        List<Unit> units = new ArrayList<>();
        BigDecimal extra = paddingCm.multiply(BigDecimal.TWO);
        for (Item item : items) {
            if (item.quantity() < 1 || item.weightGrams() < 1 || item.length() == null || item.width() == null
                    || item.height() == null || item.length().signum() <= 0 || item.width().signum() <= 0
                    || item.height().signum() <= 0) {
                throw ApiException.invalidRequest("Every variant requires positive weight and dimensions.");
            }
            for (int i = 0; i < item.quantity(); i++) {
                units.add(new Unit(item, item.length().add(extra), item.width().add(extra), item.height().add(extra)));
            }
        }
        units.sort(Comparator.<Unit, BigDecimal>comparing(u -> u.length().multiply(u.width())).reversed()
                .thenComparing(u -> u.height(), Comparator.reverseOrder()).thenComparingInt(u -> u.item().variantId()));
        // Compare homogeneous sizes and mixed greedy plans, retaining at most 3 provider-priced candidates.
        List<Plan> plans = new ArrayList<>();
        for (Box box : boxes) {
            Plan plan = pack(units, List.of(box));
            if (plan != null) plans.add(plan);
        }
        Plan mixed = pack(units, boxes.stream().sorted(Comparator.comparing(b -> b.length().multiply(b.width())))
                .toList());
        if (mixed != null) plans.add(mixed);
        if (plans.isEmpty()) throw ApiException.invalidRequest("Products do not fit available boxes, packing groups or weight limits.");
        plans.sort(Comparator.comparingInt((Plan p) -> p.parcels().size())
                .thenComparingLong(p -> p.parcels().stream().mapToLong(Parcel::packagingWeight).sum())
                .thenComparing(p -> p.parcels().stream().map(v -> v.box().length().multiply(v.box().width())
                        .multiply(v.box().height())).reduce(BigDecimal.ZERO, BigDecimal::add)));
        Map<String, Plan> unique = new LinkedHashMap<>();
        for (Plan plan : plans) {
            String key = plan.parcels().stream().map(p -> p.box().id() + ":" + p.placements().toString()).toList().toString();
            unique.putIfAbsent(key, plan);
        }
        return unique.values().stream().limit(3).toList();
    }

    private Plan pack(List<Unit> units, List<Box> boxes) {
        List<OpenParcel> parcels = new ArrayList<>();
        for (Unit unit : units) {
            boolean placed = false;
            for (OpenParcel parcel : parcels) {
                if (parcel.add(unit)) { placed = true; break; }
            }
            if (!placed) {
                for (Box box : boxes) {
                    OpenParcel parcel = new OpenParcel(box);
                    if (parcel.add(unit)) { parcels.add(parcel); placed = true; break; }
                }
            }
            if (!placed) return null;
        }
        return new Plan(parcels.stream().map(OpenParcel::snapshot).toList());
    }

    private static class OpenParcel {
        final Box box;
        final List<Rect> free = new ArrayList<>();
        final List<Placement> placements = new ArrayList<>();
        int weight;
        boolean fragile;
        BigDecimal value = BigDecimal.ZERO;

        OpenParcel(Box box) {
            this.box = box;
            free.add(new Rect(BigDecimal.ZERO, BigDecimal.ZERO, box.length(), box.width()));
        }

        boolean add(Unit unit) {
            Item item = unit.item();
            if (unit.height().compareTo(box.height()) > 0 || (long) weight + item.weightGrams() > box.maxProductWeight()
                    || (item.fragile() && !box.supportsFragile())
                    || (!box.groups().isEmpty() && (item.group() == null || !box.groups().contains(item.group())))) return false;
            Fit best = null;
            for (int i = 0; i < free.size(); i++) {
                Rect r = free.get(i);
                for (boolean rotate : List.of(false, true)) {
                    BigDecimal l = rotate ? unit.width() : unit.length(), w = rotate ? unit.length() : unit.width();
                    if (l.compareTo(r.length()) > 0 || w.compareTo(r.width()) > 0) continue;
                    BigDecimal dl = r.length().subtract(l), dw = r.width().subtract(w);
                    Fit fit = new Fit(i, rotate, l, w, dl.min(dw), dl.max(dw));
                    if (best == null || fit.shortSide().compareTo(best.shortSide()) < 0
                            || (fit.shortSide().compareTo(best.shortSide()) == 0
                                && fit.longSide().compareTo(best.longSide()) < 0)) best = fit;
                }
            }
            if (best == null) return false;
            Rect r = free.remove(best.freeIndex());
            placements.add(new Placement(item.cartItemId(), item.variantId(), r.x(), r.y(), best.length(), best.width(),
                    unit.height(), best.rotated()));
            // Two disjoint guillotine rectangles preserve non-overlap without pretending this is exact bin packing.
            if (r.length().compareTo(best.length()) > 0) free.add(new Rect(r.x().add(best.length()), r.y(),
                    r.length().subtract(best.length()), best.width()));
            if (r.width().compareTo(best.width()) > 0) free.add(new Rect(r.x(), r.y().add(best.width()),
                    r.length(), r.width().subtract(best.width())));
            weight += item.weightGrams();
            fragile |= item.fragile();
            value = value.add(item.price());
            return true;
        }

        Parcel snapshot() {
            int packaging = Math.addExact(box.boxWeight(), fragile ? box.paddingWeight() : 0);
            return new Parcel(box, List.copyOf(placements), weight, packaging, Math.addExact(weight, packaging),
                    value, fragile, box.boxCost().add(box.paddingCost()));
        }
    }
}
