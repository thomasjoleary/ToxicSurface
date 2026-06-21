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

    @Test
    void rpm_belowMinimum_isIdle() {
        assertEquals(0, CleanserRange.rangeFromRpm(0, TIERS, 16f));
        assertEquals(0, CleanserRange.rangeFromRpm(15.9f, TIERS, 16f));
        assertEquals(0, CleanserRange.rangeFromRpm(-8, TIERS, 16f));
    }

    @Test
    void rpm_selectsTierByDoublingAndSaturates() {
        assertEquals(8, CleanserRange.rangeFromRpm(16, TIERS, 16f)); // first tier at min rpm
        assertEquals(16, CleanserRange.rangeFromRpm(32, TIERS, 16f));
        assertEquals(32, CleanserRange.rangeFromRpm(64, TIERS, 16f));
        assertEquals(32, CleanserRange.rangeFromRpm(100, TIERS, 16f)); // between 64 and 128 -> still tier 32
        assertEquals(64, CleanserRange.rangeFromRpm(128, TIERS, 16f));
        assertEquals(128, CleanserRange.rangeFromRpm(256, TIERS, 16f));
        assertEquals(128, CleanserRange.rangeFromRpm(8192, TIERS, 16f)); // saturates at the largest tier
    }

    @Test
    void rpm_directionDoesNotMatter() {
        assertEquals(CleanserRange.rangeFromRpm(64, TIERS, 16f), CleanserRange.rangeFromRpm(-64, TIERS, 16f));
    }
}
