// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.conversion;

/**
 * Pure helper for the surface-anchored water→sludge band (DESIGN.md §2b). Given a
 * column's water-surface Y, the current toxic ceiling, the current sludge depth and
 * the depth already applied to that chunk, it returns the inclusive Y range of water
 * that still needs converting — so escalation only ever does incremental work.
 */
public final class SludgeConversion {
    private SludgeConversion() {}

    /** Inclusive [low, high] Y range of water to convert; {@link #NONE} when there's nothing to do. */
    public record Band(int low, int high, boolean present) {
        public static final Band NONE = new Band(0, 0, false);

        public int count() {
            return present ? (high - low + 1) : 0;
        }
    }

    public static Band bandToConvert(int surfaceY, int currentToxicY, int currentDepth, int appliedDepth) {
        if (surfaceY > currentToxicY) {
            return Band.NONE; // column's surface is still above the toxic line
        }
        if (currentDepth <= appliedDepth) {
            return Band.NONE; // band hasn't deepened since the last pass
        }
        int high = surfaceY - appliedDepth; // topmost not-yet-converted block
        int low = surfaceY - currentDepth + 1; // deepest block at the current depth
        if (low > high) {
            return Band.NONE;
        }
        return new Band(low, high, true);
    }
}
