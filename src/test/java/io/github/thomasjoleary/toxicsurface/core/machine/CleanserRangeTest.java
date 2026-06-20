// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the Cleanser range/fuel maths (DESIGN.md §3, §10). */
class CleanserRangeTest {
    private static final List<Integer> TIERS = List.of(8, 16, 32, 64, 128);
    private static final int MAX = 128;

    @Test
    void noSignal_usesMenuValueClampedToMax() {
        assertEquals(24, CleanserRange.effectiveRange(24, 0, TIERS, MAX));
        assertEquals(128, CleanserRange.effectiveRange(999, 0, TIERS, MAX));
        assertEquals(1, CleanserRange.effectiveRange(0, 0, TIERS, MAX));
    }

    @Test
    void signal_selectsTierAndSaturates() {
        assertEquals(8, CleanserRange.effectiveRange(24, 1, TIERS, MAX));
        assertEquals(32, CleanserRange.effectiveRange(24, 3, TIERS, MAX));
        assertEquals(128, CleanserRange.effectiveRange(24, 5, TIERS, MAX));
        assertEquals(128, CleanserRange.effectiveRange(24, 15, TIERS, MAX)); // saturates at last tier
    }

    @Test
    void tierSelection_isClampedToMax() {
        assertEquals(64, CleanserRange.effectiveRange(24, 15, TIERS, 64));
    }

    @Test
    void fuelCost_isOneAtOrBelowBaseAndGrowsWithExponent() {
        assertEquals(1.0, CleanserRange.fuelCostMultiplier(8, 2.0), 1e-9);
        assertEquals(1.0, CleanserRange.fuelCostMultiplier(4, 2.0), 1e-9);
        assertEquals(4.0, CleanserRange.fuelCostMultiplier(16, 2.0), 1e-9); // (16/8)^2
        assertTrue(CleanserRange.fuelCostMultiplier(32, 2.0) > CleanserRange.fuelCostMultiplier(16, 2.0));
    }
}
