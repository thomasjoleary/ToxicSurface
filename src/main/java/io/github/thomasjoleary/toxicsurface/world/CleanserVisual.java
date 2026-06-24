// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The visible "clean air dome" of a running Cleanser (DESIGN.md §3 Cleanser). Server-spawned green
 * particles — a gentle aura over the machine plus a sampling of its purge-sphere boundary — so the
 * protected zone reads in-world. Spawned server-side, so they broadcast to every nearby client with
 * no extra networking; shared by both the fuel and Mechanical Cleanser. Throttled and sparse to stay
 * cheap (boundary points far past particle render range simply aren't drawn by clients).
 */
public final class CleanserVisual {
    private static final int INTERVAL_TICKS = 4;
    private static final int SHELL_SAMPLES = 6;

    private CleanserVisual() {}

    /** Emits the aura/boundary particles for a Cleanser running at {@code range}, throttled by tick. */
    public static void tick(ServerLevel level, BlockPos pos, int range) {
        if (level.getGameTime() % INTERVAL_TICKS != 0 || range <= 0) {
            return;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        // Ambient aura just above the machine — always visible when you're near it.
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy + 0.9, cz, 2, 0.25, 0.25, 0.25, 0.0);

        // A few random points on the purge-sphere boundary to trace the protected edge.
        RandomSource random = level.getRandom();
        for (int i = 0; i < SHELL_SAMPLES; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER, cx + sx * range, cy + sy * range, cz + sz * range, 1, 0, 0, 0, 0.0);
        }
    }
}
