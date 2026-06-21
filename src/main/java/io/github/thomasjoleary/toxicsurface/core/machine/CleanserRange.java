// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.machine;

import java.util.List;

/**
 * Pure range/fuel maths for the Cleanser (DESIGN.md §3 Cleanser). Kept Minecraft-free so
 * the menu-vs-redstone range resolution and the fuel-cost curve are unit-testable.
 *
 * <p>The menu value is the primary control. A redstone signal is an optional on-the-fly
 * override: while powered (signal &gt; 0) the signal strength picks a preset tier; with no
 * signal the menu value is used. All ranges are clamped to {@code maxRange}.
 */
public final class CleanserRange {
    /** The smallest meaningful range; also the fuel-cost baseline (cost = 1.0 at this range). */
    public static final int BASE_RANGE = 8;

    /**
     * Minimum absolute rotation speed (RPM) the Mechanical Cleanser needs to run at the
     * smallest range tier. Below this the kinetic machine is idle. Each subsequent tier needs
     * double the speed (mirrors Create's gear-train doubling), so the speed-to-range curve is
     * the rotational analogue of the fuel Cleanser's redstone tier selection.
     */
    public static final float MIN_RPM = 16f;

    private CleanserRange() {}

    /**
     * Maps an absolute rotation speed onto the ascending tier list for the Mechanical Cleanser
     * (DESIGN.md §3 Cleanser "Create variant"). Returns {@code 0} when {@code |rpm|} is below
     * {@code minRpm} (machine idle); otherwise each successive tier requires double the speed of
     * the previous one, saturating at the largest tier.
     *
     * @param rpm signed rotation speed; only the magnitude matters (either direction drives it)
     * @param tiers ascending preset radii (same list the redstone Cleanser uses)
     * @param minRpm speed needed for the first tier
     * @return the selected radius, or 0 if there isn't enough speed to run
     */
    public static int rangeFromRpm(float rpm, List<? extends Integer> tiers, float minRpm) {
        float abs = Math.abs(rpm);
        if (tiers == null || tiers.isEmpty() || minRpm <= 0 || abs < minRpm) {
            return 0;
        }
        int index = 0;
        float threshold = minRpm;
        for (int i = 0; i < tiers.size() && abs >= threshold; i++) {
            index = i;
            threshold *= 2f;
        }
        return tiers.get(index);
    }

    /**
     * Resolves the effective sphere radius.
     *
     * @param menuRange the radius set in the menu (primary control)
     * @param redstoneSignal current redstone power 0..15 (0 = use the menu value)
     * @param tiers ascending preset radii the signal selects between
     * @param maxRange hard cap from config
     */
    public static int effectiveRange(int menuRange, int redstoneSignal, List<? extends Integer> tiers, int maxRange) {
        int range = redstoneSignal > 0 ? tierForSignal(redstoneSignal, tiers) : menuRange;
        return clamp(range, maxRange);
    }

    /** Maps a 1..15 signal onto the tier list (signal 1 -> first tier, saturating at the last). */
    public static int tierForSignal(int signal, List<? extends Integer> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return BASE_RANGE;
        }
        int index = Math.min(tiers.size() - 1, Math.max(0, signal - 1));
        return tiers.get(index);
    }

    public static int clamp(int range, int maxRange) {
        return Math.max(1, Math.min(maxRange, range));
    }

    /**
     * Fuel-cost multiplier relative to the base furnace rate: {@code (range / BASE_RANGE)^k}.
     * At or below {@link #BASE_RANGE} the cost is 1.0.
     */
    public static double fuelCostMultiplier(int range, double exponent) {
        if (range <= BASE_RANGE) {
            return 1.0;
        }
        return Math.pow((double) range / BASE_RANGE, exponent);
    }
}
