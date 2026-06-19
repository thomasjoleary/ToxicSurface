// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.equipment;

/**
 * Pure filter-time accounting for masks and the hazmat suit (DESIGN.md §3). Kept
 * Minecraft-free so the consume/expire logic is unit-testable.
 */
public final class MaskFilter {
    private MaskFilter() {}

    /** Remaining ticks after consuming {@code deltaTicks}, clamped at zero. */
    public static int consume(int remaining, int deltaTicks) {
        return Math.max(0, remaining - deltaTicks);
    }

    public static boolean isActive(int remaining) {
        return remaining > 0;
    }

    /** True only on the step where an active filter runs out — triggers the expiry warning. */
    public static boolean justExpired(int before, int after) {
        return before > 0 && after <= 0;
    }
}
