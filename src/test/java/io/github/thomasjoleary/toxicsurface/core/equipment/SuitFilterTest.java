// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thomasjoleary.toxicsurface.core.equipment.SuitFilter.State;
import org.junit.jupiter.api.Test;

/** Tests for the hazmat chest filter accounting (DESIGN.md §3, §10). */
class SuitFilterTest {

    private static final int DURATION = 2400;

    @Test
    void burnsActiveFilterWithoutLosingCount() {
        State s = SuitFilter.consume(10, 2400, 5, DURATION);
        assertEquals(10, s.filters());
        assertEquals(2395, s.activeTicks());
    }

    @Test
    void pullsNextFilterWhenActiveRunsOut() {
        State s = SuitFilter.consume(10, 3, 5, DURATION);
        assertEquals(9, s.filters());
        assertEquals(2398, s.activeTicks()); // -2 + 2400
    }

    @Test
    void lastFilterExhausts_toEmpty() {
        State s = SuitFilter.consume(1, 3, 5, DURATION);
        assertEquals(0, s.filters());
        assertEquals(0, s.activeTicks());
    }

    @Test
    void emptyStaysEmpty() {
        State s = SuitFilter.consume(0, 0, 5, DURATION);
        assertEquals(0, s.filters());
    }

    @Test
    void isActiveAndJustExpired() {
        assertTrue(SuitFilter.isActive(1));
        assertFalse(SuitFilter.isActive(0));
        assertTrue(SuitFilter.justExpired(1, 0));
        assertFalse(SuitFilter.justExpired(0, 0));
        assertFalse(SuitFilter.justExpired(3, 2));
    }
}
