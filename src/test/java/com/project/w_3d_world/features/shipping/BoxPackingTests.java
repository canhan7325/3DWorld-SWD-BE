package com.project.w_3d_world.features.shipping;

import static org.junit.jupiter.api.Assertions.*;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.shipping.packing.BoxPackingService;
import com.project.w_3d_world.features.shipping.packing.BoxPackingService.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class BoxPackingTests {
    BoxPackingService service = new BoxPackingService();
    BigDecimal d(String value) { return new BigDecimal(value); }
    Item item(int id, int quantity, String l, String w, String h, boolean fragile, String group) {
        return new Item(id, id, quantity, 100, d(l), d(w), d(h), fragile, group, d("10000"));
    }
    Box box(String l, String w, String h, int capacity, boolean fragile, Set<String> groups) {
        return new Box(1, "BOX", d(l), d(w), d(h), capacity, 50, 20, fragile, groups, d("2000"), d("1000"));
    }
    @Test
    void paddingMakesIllustratedSmallBoxInvalid() {
        var product = item(1, 1, "15", "12", "10", true, "MODEL");
        assertThrows(ApiException.class, () -> service.suggest(List.of(product),
                List.of(box("20", "15", "12", 500, true, Set.of())), d("2")));
        var parcel = service.suggest(List.of(product), List.of(box("20", "17", "15", 500, true, Set.of())), d("2"))
                .getFirst().parcels().getFirst();
        assertEquals(d("14"), parcel.placements().getFirst().height());
        assertEquals(170, parcel.totalWeight());
    }
    @Test
    void onlyFloorRotationAndNoStacking() {
        var rotated = service.suggest(List.of(item(1, 1, "8", "4", "3", false, null)),
                List.of(box("4", "8", "3", 1000, false, Set.of())), BigDecimal.ZERO);
        assertTrue(rotated.getFirst().parcels().getFirst().placements().getFirst().rotated());
        var plan = service.suggest(List.of(item(1, 2, "4", "4", "2", false, null)),
                List.of(box("4", "4", "20", 1000, false, Set.of())), BigDecimal.ZERO).getFirst();
        assertEquals(2, plan.parcels().size());
        assertThrows(ApiException.class, () -> service.suggest(List.of(item(1, 1, "4", "4", "10", false, null)),
                List.of(box("20", "20", "5", 1000, false, Set.of())), BigDecimal.ZERO));
    }
    @Test
    void weightGroupAndFragileRestrictionsAreEnforced() {
        assertThrows(ApiException.class, () -> service.suggest(List.of(item(1, 1, "1", "1", "1", true, "A")),
                List.of(box("20", "20", "20", 1000, false, Set.of())), BigDecimal.ZERO));
        assertThrows(ApiException.class, () -> service.suggest(List.of(item(1, 1, "1", "1", "1", false, null)),
                List.of(box("20", "20", "20", 1000, false, Set.of("A"))), BigDecimal.ZERO));
        var plan = service.suggest(List.of(item(1, 2, "1", "1", "1", false, "A")),
                List.of(box("20", "20", "20", 100, false, Set.of("A"))), BigDecimal.ZERO).getFirst();
        assertEquals(2, plan.parcels().size());
    }
    @Test
    void placementsNeverOverlapAndAllUnitsAreAllocated() {
        var plan = service.suggest(List.of(item(1, 4, "5", "4", "3", false, null),
                item(2, 3, "6", "3", "2", false, null)),
                List.of(box("20", "20", "10", 1000, true, Set.of())), d("1")).getFirst();
        assertEquals(7, plan.parcels().stream().mapToInt(p -> p.placements().size()).sum());
        for (Parcel parcel : plan.parcels()) {
            var positions = parcel.placements();
            for (int i = 0; i < positions.size(); i++) {
                Placement a = positions.get(i);
                assertTrue(a.x().add(a.length()).compareTo(parcel.box().length()) <= 0);
                assertTrue(a.y().add(a.width()).compareTo(parcel.box().width()) <= 0);
                for (int j = i + 1; j < positions.size(); j++) {
                    Placement b = positions.get(j);
                    assertTrue(a.x().add(a.length()).compareTo(b.x()) <= 0 || b.x().add(b.length()).compareTo(a.x()) <= 0
                            || a.y().add(a.width()).compareTo(b.y()) <= 0 || b.y().add(b.width()).compareTo(a.y()) <= 0);
                }
            }
        }
    }
}
