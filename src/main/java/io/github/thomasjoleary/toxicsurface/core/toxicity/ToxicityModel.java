// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.toxicity;

/**
 * Pure escalation math for the toxic ceiling (DESIGN.md §3 Escalation, §2b sludge
 * band). No Minecraft dependency, so the rising-line and proportional sludge-depth
 * formulas can be unit-tested directly.
 */
public final class ToxicityModel {
    /** Minecraft day length in ticks. */
    public static final int TICKS_PER_DAY = 24_000;

    private ToxicityModel() {}

    /**
     * Current toxic ceiling Y, {@code elapsedTicksSinceStart} after toxicity began.
     *
     * <p>The line starts at {@code startY} and rises continuously at
     * {@code spreadBlocksPerDay}, capped at {@code maxY}. A spread of {@code 0} (or a
     * non-positive elapsed time) keeps it static at {@code startY}.
     */
    public static int currentToxicY(
            long elapsedTicksSinceStart, int startY, int spreadBlocksPerDay, int maxY, int ticksPerDay) {
        long cap = Math.max(startY, maxY); // ceiling never sits below the start line
        if (elapsedTicksSinceStart <= 0 || spreadBlocksPerDay <= 0) {
            return (int) Math.min(cap, startY);
        }
        long risen = (long) spreadBlocksPerDay * elapsedTicksSinceStart / ticksPerDay;
        return (int) Math.min(cap, (long) startY + risen);
    }

    /** Convenience overload using the vanilla {@link #TICKS_PER_DAY}. */
    public static int currentToxicY(long elapsedTicksSinceStart, int startY, int spreadBlocksPerDay, int maxY) {
        return currentToxicY(elapsedTicksSinceStart, startY, spreadBlocksPerDay, maxY, TICKS_PER_DAY);
    }

    /**
     * Sludge band depth for the current ceiling (DESIGN.md §2b). Scales
     * <em>proportionally</em> between {@code depthMin} (at the start line) and
     * {@code depthMax} (when the ceiling reaches {@code maxY}) — anchored to the water
     * surface, so escalation deepens the skin rather than sweeping up the column.
     */
    public static int sludgeDepth(int currentToxicY, int startY, int maxY, int depthMin, int depthMax) {
        double progress = (maxY <= startY) ? 0.0 : clamp01((double) (currentToxicY - startY) / (maxY - startY));
        long depth = Math.round(depthMin + progress * (depthMax - depthMin));
        int lo = Math.min(depthMin, depthMax);
        int hi = Math.max(depthMin, depthMax);
        return (int) Math.max(lo, Math.min(hi, depth));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
