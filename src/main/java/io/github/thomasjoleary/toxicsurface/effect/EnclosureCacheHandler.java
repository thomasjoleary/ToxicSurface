// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureCache;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.enclosure.ScanResult;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Wires the connected-component {@link EnclosureCache} (DESIGN.md §2a, §8) into the live gas effect.
 * Holds one cache per dimension so the expensive sealing flood-fill ({@link GasEffectHandler})
 * runs once per air pocket instead of once per exposed player every cycle, and drops cached pockets
 * when a block change could have breached their seal.
 *
 * <p>Invalidation listens to the block-change events that move a sealing block: breaking, placing
 * (single and multi), fluid-formed blocks, explosions, and piston pushes/pulls. Changes that fire
 * <b>no event</b> ({@code /setblock}, {@code /fill}, {@code /clone}, worldgen, direct {@code setBlock}
 * from other mods) are caught by the cache's TTL ({@link #MAX_AGE_TICKS}): a stale seal self-heals
 * within that window. Server-thread only; caches are cleared when their level unloads.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class EnclosureCacheHandler {
    /** Longest a sealed pocket is trusted before a re-scan — the TTL safety net for untracked changes. */
    private static final long MAX_AGE_TICKS = 200; // 10 seconds

    /** A piston moves a contiguous column of up to this many blocks along its facing. */
    private static final int MAX_PISTON_PUSH = 12;

    private static final Map<ResourceKey<Level>, EnclosureCache> CACHES = new HashMap<>();

    private EnclosureCacheHandler() {}

    /**
     * Whether the cell at {@code (x,y,z)} is in a sealed air pocket, using the per-dimension cache.
     * On a miss it runs the bounded flood-fill and caches a sealed result (exposed results are left
     * uncached and recomputed on the next throttle cycle, per §2a).
     */
    public static boolean isSealed(ServerLevel level, int x, int y, int z, int budget) {
        EnclosureCache cache = CACHES.computeIfAbsent(
                level.dimension(), key -> new EnclosureCache(EnclosureCache.DEFAULT_CAPACITY, MAX_AGE_TICKS));
        long now = level.getGameTime();
        if (cache.get(x, y, z, now) != null) {
            return true; // only sealed pockets are ever cached
        }
        ScanResult result = EnclosureScanner.scan(x, y, z, new LevelPassabilityProbe(level), budget);
        cache.putSealed(result, now);
        return result.isSealed();
    }

    private static void invalidate(LevelAccessor levelAccessor, BlockPos pos) {
        if (levelAccessor instanceof ServerLevel level) {
            EnclosureCache cache = CACHES.get(level.dimension());
            if (cache != null) {
                cache.invalidate(pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            invalidate(event.getLevel(), snapshot.getPos());
        }
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        for (BlockPos pos : event.getAffectedBlocks()) {
            invalidate(event.getLevel(), pos);
        }
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Post event) {
        // A piston shifts a contiguous column of blocks one cell along its facing, so invalidate the
        // piston itself plus the whole movement line (every moved block's source and destination).
        invalidate(event.getLevel(), event.getPos());
        Direction dir = event.getDirection();
        BlockPos pos = event.getFaceOffsetPos();
        for (int i = 0; i <= MAX_PISTON_PUSH; i++) {
            invalidate(event.getLevel(), pos);
            pos = pos.relative(dir);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CACHES.remove(level.dimension());
        }
    }
}
