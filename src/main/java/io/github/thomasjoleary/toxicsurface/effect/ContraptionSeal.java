// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Soft hook for "is the player sealed inside a moving Create contraption?" (DESIGN.md §9). The world
 * flood-fill ({@code EnclosureCacheHandler}) can't see contraption rooms because an assembled
 * structure's blocks live in the contraption entity, not the world — so the gas check delegates the
 * contraption case here. The Create-only implementation registers itself via {@link #setImpl} when
 * Create loads; without Create the default returns {@code false}, so base code never references the
 * kinetic API.
 */
public final class ContraptionSeal {
    @FunctionalInterface
    public interface Check {
        boolean isSealed(ServerLevel level, Player player, int budget);
    }

    private static volatile Check impl = (level, player, budget) -> false;
    private static volatile boolean loggedFailure;

    private ContraptionSeal() {}

    /** Installed by the Create compat layer at load; replaces the no-op default. */
    public static void setImpl(Check check) {
        impl = check;
    }

    /**
     * Whether the player stands in a sealed pocket of a contraption they are riding/standing on. Runs
     * in the per-player gas tick, so any unexpected Create-side error fails safe to {@code false}
     * (treated as not sealed — the pre-contraption behaviour) and is logged once rather than crashing.
     */
    public static boolean isSealed(ServerLevel level, Player player, int budget) {
        try {
            return impl.isSealed(level, player, budget);
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                ToxicSurface.LOGGER.error("Contraption seal check failed; treating as unsealed", t);
            }
            return false;
        }
    }
}
