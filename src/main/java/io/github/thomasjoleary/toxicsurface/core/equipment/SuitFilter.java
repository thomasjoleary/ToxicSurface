// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.equipment;

/**
 * Pure filter accounting for the hazmat chestpiece (DESIGN.md §3 Hazmat suit). The
 * chest holds a stack of whole filters; one burns at a time ({@code activeTicks}),
 * and when it runs out the next filter is pulled automatically. Protection lasts only
 * while filters remain — the suit is not unconditional immunity, it just carries more
 * charge and burns it at half the mask's rate.
 */
public final class SuitFilter {
    private SuitFilter() {}

    /** Result of a consumption step: remaining whole filters and the active filter's ticks. */
    public record State(int filters, int activeTicks) {}

    /**
     * Burns {@code deltaTicks} of charge (the caller pre-scales by the suit's rate
     * factor), pulling fresh filters as needed. Returns the depleted state once the
     * last filter is gone.
     */
    public static State consume(int filters, int activeTicks, int deltaTicks, int filterDurationTicks) {
        if (filters <= 0) {
            return new State(0, 0);
        }
        int remainingFilters = filters;
        int active = activeTicks - deltaTicks;
        while (active <= 0 && remainingFilters > 1) {
            remainingFilters--;
            active += filterDurationTicks;
        }
        if (remainingFilters <= 1 && active <= 0) {
            return new State(0, 0); // last filter exhausted
        }
        return new State(remainingFilters, active);
    }

    public static boolean isActive(int filters) {
        return filters > 0;
    }

    /** True only on the step where the last filter runs out — triggers the expiry warning. */
    public static boolean justExpired(int filtersBefore, int filtersAfter) {
        return filtersBefore > 0 && filtersAfter <= 0;
    }
}
