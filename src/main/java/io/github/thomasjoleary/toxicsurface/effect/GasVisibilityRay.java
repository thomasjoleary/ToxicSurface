// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import io.github.thomasjoleary.toxicsurface.world.SmogClouds;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * How far a player can see actual exposed gas along their view direction (DESIGN.md §3 gas
 * visibility) — the missing piece for {@code ToxicVolumetricFog}: a per-pixel depth reconstruction
 * alone can only tell "is this surface below the toxic ceiling," which can't distinguish a sealed
 * room's own (safe) far wall from genuinely exposed exterior ground at the same distance, since both
 * are just "a solid surface below the ceiling Y" to a depth buffer. This walks the same eye-to-view
 * ray a fraction of a second at a time and asks the mod's actual sealing/cleanser/smog rules (the
 * same ones {@link GasEffectHandler} already evaluates for damage) whether each sampled cell is truly
 * exposed, so the client can suppress haze up to the point that's actually confirmed safe.
 *
 * <p>Approximation, not exact: it only samples one direction (the view ray) and applies that single
 * distance across the whole screen, and — unlike {@link GasEffectHandler}'s own per-player check —
 * doesn't consult {@link ContraptionSeal} (which is keyed to the player's own position, not arbitrary
 * ray samples). Good enough for "don't haze up the inside of my own sealed room," not a substitute
 * for the real per-player exposure check that still gates damage.
 */
public final class GasVisibilityRay {
    private static final float STEP = 2.0f;
    private static final float MAX_DISTANCE = 48f;

    /** Returned when no exposed gas is found within {@link #MAX_DISTANCE} (or the ray hits a wall first). */
    public static final float NONE_FOUND = MAX_DISTANCE;

    private GasVisibilityRay() {}

    /** Distance from {@code player}'s eye, along their look direction, to the nearest exposed gas cell. */
    public static float distanceToExposedGas(ServerLevel level, Player player, int budget) {
        if (!ToxicityTicker.isAffected(level)) {
            return NONE_FOUND;
        }
        int ceiling = ToxicityTicker.currentToxicY(level);
        if (ceiling == ToxicityTicker.NOT_TOXIC) {
            return NONE_FOUND;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0f);
        for (float d = STEP; d <= MAX_DISTANCE; d += STEP) {
            Vec3 p = eye.add(dir.scale(d));
            int x = Mth.floor(p.x);
            int y = Mth.floor(p.y);
            int z = Mth.floor(p.z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return NONE_FOUND; // the ray can't see past a solid surface; everything up to it is safe
            }
            if (!level.getFluidState(pos).isEmpty()) {
                continue; // submerged cells aren't gas, but the ray keeps going (e.g. across a lake)
            }
            boolean inSmog = SmogClouds.isInside(level, x, y, z);
            boolean ambient = y <= ceiling;
            if (!ambient && !inSmog) {
                continue; // above the ceiling with no smog: not toxic here
            }
            if (CleanserBubbles.isInside(level, x, y, z)) {
                continue;
            }
            if (!EnclosureCacheHandler.isSealed(level, x, y, z, budget)) {
                return d; // confirmed exposed toxic gas at this distance
            }
        }
        return NONE_FOUND;
    }
}
