// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the filter-time accounting (DESIGN.md §3, §10). */
class MaskFilterTest {

    @Test
    void consume_decrementsAndClampsAtZero() {
        assertEquals(290, MaskFilter.consume(300, 10));
        assertEquals(0, MaskFilter.consume(5, 10));
    }

    @Test
    void isActive_whenPositive() {
        assertTrue(MaskFilter.isActive(1));
        assertFalse(MaskFilter.isActive(0));
    }

    @Test
    void justExpired_onlyOnTheCrossingStep() {
        assertTrue(MaskFilter.justExpired(5, 0)); // ran out this step
        assertFalse(MaskFilter.justExpired(0, 0)); // already empty
        assertFalse(MaskFilter.justExpired(20, 10)); // still active
    }
}
