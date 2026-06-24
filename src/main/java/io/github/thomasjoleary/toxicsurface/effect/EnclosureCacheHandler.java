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

/**
 * Wires the connected-component {@link EnclosureCache} (DESIGN.md §2a, §8) into the live gas effect.
 * Holds one cache per dimension so the expensive sealing flood-fill ({@link GasEffectHandler})
 * runs once per air pocket instead of once per exposed player every cycle, and drops cached pockets
 * when a block change could have breached their seal.
 *
 * <p>Invalidation listens to the block-change events that move a sealing block: breaking, placing
 * (single and multi), fluid-formed blocks, and explosions — the player-driven cases that make or
 * break a seal. Server-thread only; caches are cleared when their level unloads.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class EnclosureCacheHandler {
    private static final Map<ResourceKey<Level>, EnclosureCache> CACHES = new HashMap<>();

    private EnclosureCacheHandler() {}

    /**
     * Whether the cell at {@code (x,y,z)} is in a sealed air pocket, using the per-dimension cache.
     * On a miss it runs the bounded flood-fill and caches a sealed result (exposed results are left
     * uncached and recomputed on the next throttle cycle, per §2a).
     */
    public static boolean isSealed(ServerLevel level, int x, int y, int z, int budget) {
        EnclosureCache cache = CACHES.computeIfAbsent(level.dimension(), key -> new EnclosureCache());
        if (cache.get(x, y, z) != null) {
            return true; // only sealed pockets are ever cached
        }
        ScanResult result = EnclosureScanner.scan(x, y, z, new LevelPassabilityProbe(level), budget);
        cache.putSealed(result);
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
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CACHES.remove(level.dimension());
        }
    }
}
