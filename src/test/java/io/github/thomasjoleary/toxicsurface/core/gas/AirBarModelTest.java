// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the toxic air bar stepping function (DESIGN.md §3, §10). */
class AirBarModelTest {

    private static final int DRAIN = 300; // 15s
    private static final int REFILL = 60; // 3s
    private static final int DELTA = 10; // throttle interval

    @Test
    void drains_whileExposed() {
        assertEquals(290, AirBarModel.step(300, true, DRAIN, REFILL, DELTA));
    }

    @Test
    void drainsToEmptyOverDrainTicks() {
        int air = AirBarModel.fullAir(DRAIN);
        for (int t = 0; t < DRAIN; t += DELTA) {
            air = AirBarModel.step(air, true, DRAIN, REFILL, DELTA);
        }
        assertEquals(0, air);
    }

    @Test
    void clampsAtZero() {
        assertEquals(0, AirBarModel.step(5, true, DRAIN, REFILL, DELTA));
    }

    @Test
    void refills_whenClean() {
        // refillPerTick = ceil(300/60) = 5; over 10 ticks -> +50.
        assertEquals(50, AirBarModel.step(0, false, DRAIN, REFILL, DELTA));
    }

    @Test
    void clampsAtFull() {
        assertEquals(DRAIN, AirBarModel.step(295, false, DRAIN, REFILL, DELTA));
    }

    @Test
    void refillPerTick_isCeil() {
        assertEquals(5, AirBarModel.refillPerTick(300, 60));
        assertEquals(1, AirBarModel.refillPerTick(60, 300)); // never below 1
    }

    @Test
    void takesDamage_onlyWhenEmptyAndExposed() {
        assertTrue(AirBarModel.takesDamage(0, true));
        assertFalse(AirBarModel.takesDamage(1, true));
        assertFalse(AirBarModel.takesDamage(0, false));
    }
}
