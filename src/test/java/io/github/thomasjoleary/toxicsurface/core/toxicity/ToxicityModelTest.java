// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.toxicity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the escalation and sludge-depth math (DESIGN.md §2b, §3, §10). */
class ToxicityModelTest {

    private static final int START_Y = 63;
    private static final int MAX_Y = 200;
    private static final int DAY = ToxicityModel.TICKS_PER_DAY;

    @Test
    void staticLine_whenSpreadZero() {
        assertEquals(START_Y, ToxicityModel.currentToxicY(100L * DAY, START_Y, 0, MAX_Y));
    }

    @Test
    void beforeStart_staysAtStart() {
        assertEquals(START_Y, ToxicityModel.currentToxicY(0, START_Y, 4, MAX_Y));
    }

    @Test
    void risesContinuouslyWithSpread() {
        assertEquals(START_Y + 4, ToxicityModel.currentToxicY(DAY, START_Y, 4, MAX_Y)); // 1 day
        assertEquals(START_Y + 2, ToxicityModel.currentToxicY(DAY / 2, START_Y, 4, MAX_Y)); // half day
        assertEquals(START_Y + 40, ToxicityModel.currentToxicY(10L * DAY, START_Y, 4, MAX_Y)); // 10 days
    }

    @Test
    void capsAtMaxY() {
        assertEquals(MAX_Y, ToxicityModel.currentToxicY(1000L * DAY, START_Y, 4, MAX_Y));
    }

    @Test
    void sludgeDepth_minAtStartLine() {
        assertEquals(4, ToxicityModel.sludgeDepth(START_Y, START_Y, MAX_Y, 4, 24));
    }

    @Test
    void sludgeDepth_maxAtCeiling() {
        assertEquals(24, ToxicityModel.sludgeDepth(MAX_Y, START_Y, MAX_Y, 4, 24));
    }

    @Test
    void sludgeDepth_proportionalAtMidpoint() {
        // Range 63..263, midpoint 163 -> progress 0.5 -> 4 + 0.5 * (24-4) = 14.
        assertEquals(14, ToxicityModel.sludgeDepth(163, 63, 263, 4, 24));
    }

    @Test
    void sludgeDepth_clampedWhenBeyondCeiling() {
        assertEquals(24, ToxicityModel.sludgeDepth(MAX_Y + 50, START_Y, MAX_Y, 4, 24));
    }

    @Test
    void sludgeDepth_minWhenNoEscalationRange() {
        assertEquals(4, ToxicityModel.sludgeDepth(70, 63, 63, 4, 24));
    }
}
