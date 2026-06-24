// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import net.minecraft.server.level.ServerLevel;

/**
 * The "worsens the apocalypse" drawback (DESIGN.md §7 Toxic generators). Every tick a toxic
 * generator runs, it dumps contamination into its dimension: pollution ticks accumulate on the
 * per-dimension {@link ToxicityState} and are added to the elapsed time the escalation model
 * sees ({@link ToxicityTicker}). The net effect is that burning waste for power makes the toxic
 * ceiling rise faster — and can even bring the world's first turn toxic forward. Only affected
 * dimensions escalate, so pollution is ignored elsewhere (the smog cloud still vents anywhere).
 */
public final class Pollution {
    private Pollution() {}

    /** Adds {@code ticks} of escalation pollution to {@code level}'s toxicity state. */
    public static void add(ServerLevel level, int ticks) {
        if (ticks <= 0 || !ToxicityTicker.isAffected(level)) {
            return;
        }
        ToxicityState.get(level).addPollution(ticks);
    }
}
