// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The shared drawbacks both toxic generators carry while running (DESIGN.md §7). Factored out
 * of the Create-side block entities so the fuel ({@code waste_generator}) and fluid
 * ({@code sludge_generator}) variants vent identically, and so this — the config-reading,
 * world-mutating glue — stays free of any Create types. Two drawbacks:
 *
 * <ul>
 *   <li><b>Smog cloud</b> — a {@link SmogClouds} sphere of toxic air around the machine.
 *   <li><b>Pollution</b> — {@link Pollution} that accelerates the dimension's escalation.
 * </ul>
 */
public final class GeneratorEmissions {
    private GeneratorEmissions() {}

    /** Vents the generator's smog + pollution for one running tick. */
    public static void emit(ServerLevel level, BlockPos pos) {
        int radius = ToxicSurfaceConfig.GENERATOR_SMOG_RADIUS.get();
        if (radius > 0) {
            SmogClouds.update(level, pos, radius);
        } else {
            SmogClouds.remove(level, pos);
        }
        Pollution.add(level, ToxicSurfaceConfig.GENERATOR_POLLUTION_PER_TICK.get());
    }

    /** Collapses the smog when the generator idles or is removed. */
    public static void stop(ServerLevel level, BlockPos pos) {
        SmogClouds.remove(level, pos);
    }
}
