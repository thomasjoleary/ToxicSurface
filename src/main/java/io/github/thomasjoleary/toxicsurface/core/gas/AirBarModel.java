// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

/**
 * Drowning-style toxic air bar (DESIGN.md §3 Toxic air bar). Air is measured in
 * "breath ticks": a full bar holds {@code drainTicks} units and loses one unit per
 * game tick while exposed, so it empties in {@code drainTicks} ticks. When clean it
 * refills to full over {@code refillTicks} ticks. Pure stepping function — the
 * handler calls it with the number of ticks elapsed since the last update.
 */
public final class AirBarModel {
    private AirBarModel() {}

    /** Full bar value (one unit drains per tick, so this equals the drain duration). */
    public static int fullAir(int drainTicks) {
        return drainTicks;
    }

    /** Units refilled per tick so an empty bar returns to full in {@code refillTicks}. */
    public static int refillPerTick(int drainTicks, int refillTicks) {
        return Math.max(1, (int) Math.ceil((double) drainTicks / refillTicks));
    }

    /**
     * Advances the bar by {@code deltaTicks}: drains while {@code exposed}, otherwise
     * refills toward full. Result is clamped to {@code [0, fullAir]}.
     */
    public static int step(int currentAir, boolean exposed, int drainTicks, int refillTicks, int deltaTicks) {
        int full = fullAir(drainTicks);
        int air = Math.max(0, Math.min(full, currentAir));
        if (exposed) {
            return Math.max(0, air - deltaTicks);
        }
        return Math.min(full, air + refillPerTick(drainTicks, refillTicks) * deltaTicks);
    }

    /** True when the bar is empty and still exposed — toxic damage applies (DESIGN.md §3). */
    public static boolean takesDamage(int air, boolean exposed) {
        return exposed && air <= 0;
    }
}
