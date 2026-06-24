// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig.WaterConversionMode;
import io.github.thomasjoleary.toxicsurface.core.conversion.SludgeConversion;
import io.github.thomasjoleary.toxicsurface.core.conversion.SludgeConversion.Band;
import io.github.thomasjoleary.toxicsurface.core.toxicity.ToxicityModel;
import io.github.thomasjoleary.toxicsurface.registry.ModAttachments;
import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import io.github.thomasjoleary.toxicsurface.registry.ModFluids;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Lazy, budgeted water→sludge conversion (DESIGN.md §2b, §3, §8). Toxified chunks are
 * queued and drained at a fixed blocks-per-tick budget; each chunk converts the
 * surface-anchored band given by {@link SludgeConversion}, recording the applied depth so
 * escalation only ever does incremental work and no block is converted twice.
 *
 * <p>Chunks reach the queue two ways: on {@link ChunkEvent.Load} (newly loaded toxified chunks),
 * and via a sweep of already-loaded chunks the moment the band first appears (activation) or
 * deepens (escalation) — those chunks fire no fresh load event, so without the sweep an ocean you
 * are standing next to at activation would never get its sludge skin. A dedup set keeps the queue
 * from accumulating duplicates between the two paths.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class SludgeConversionManager {
    /** Effective band depth used in FULL mode (covers any realistic column). */
    private static final int FULL_MODE_DEPTH = 512;

    /** Sentinel for "no liquid surface found in this column". */
    private static final int NO_SURFACE = Integer.MIN_VALUE;
    /** How far below the heightmap top to look for the liquid surface (covers ice caps / off-by-one). */
    private static final int SURFACE_SCAN_DEPTH = 6;

    private static final Map<ResourceKey<Level>, ArrayDeque<Long>> QUEUES = new HashMap<>();
    /** Mirror of {@link #QUEUES} for O(1) dedup so the load + sweep paths don't double-enqueue. */
    private static final Map<ResourceKey<Level>, Set<Long>> QUEUED = new HashMap<>();
    /** Currently-loaded chunks per dimension, so the sweep can reach them without a load event. */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED = new HashMap<>();

    private static final Map<ResourceKey<Level>, Task> CURRENT = new HashMap<>();
    /** Last band depth a sweep ran for, per dimension; a rise re-sweeps loaded chunks. */
    private static final Map<ResourceKey<Level>, Integer> LAST_DEPTH = new HashMap<>();

    private SludgeConversionManager() {}

    /** A chunk being converted, with a cursor over its 256 columns. */
    private static final class Task {
        final long chunkPos;
        final int targetDepth;
        final int appliedStart;
        int columnIndex;

        Task(long chunkPos, int targetDepth, int appliedStart) {
            this.chunkPos = chunkPos;
            this.targetDepth = targetDepth;
            this.appliedStart = appliedStart;
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)
                || !ToxicityTicker.isAffected(level)) {
            return;
        }
        ResourceKey<Level> dim = level.dimension();
        long chunkPos = chunk.getPos().toLong();
        LOADED.computeIfAbsent(dim, k -> new HashSet<>()).add(chunkPos);

        int ceiling = ToxicityTicker.currentToxicY(level);
        if (ceiling == ToxicityTicker.NOT_TOXIC) {
            return;
        }
        int applied = chunk.getData(ModAttachments.APPLIED_SLUDGE_DEPTH.get());
        if (currentDepth(ceiling) > applied) {
            enqueue(dim, chunkPos);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Set<Long> loaded = LOADED.get(level.dimension());
            if (loaded != null) {
                loaded.remove(chunk.getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ResourceKey<Level> dim = level.dimension();
            QUEUES.remove(dim);
            QUEUED.remove(dim);
            LOADED.remove(dim);
            CURRENT.remove(dim);
            LAST_DEPTH.remove(dim);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && ToxicityTicker.isAffected(level)) {
            process(level);
        }
    }

    private static void process(ServerLevel level) {
        int ceiling = ToxicityTicker.currentToxicY(level);
        if (ceiling == ToxicityTicker.NOT_TOXIC) {
            return;
        }
        ResourceKey<Level> dim = level.dimension();
        int depthNow = currentDepth(ceiling);

        // The band just appeared (activation) or deepened (escalation): already-loaded chunks fired
        // no fresh load event, so sweep them into the queue now (DESIGN.md §2b).
        Integer lastDepth = LAST_DEPTH.get(dim);
        if (lastDepth == null || depthNow > lastDepth) {
            sweepLoadedChunks(level, dim, depthNow);
            LAST_DEPTH.put(dim, depthNow);
        }

        int budget = ToxicSurfaceConfig.WATER_CONVERSION_BLOCKS_PER_TICK.get();

        while (budget > 0) {
            Task task = CURRENT.get(dim);
            if (task == null) {
                Long pos = pollQueue(dim);
                if (pos == null) {
                    return;
                }
                ChunkPos cp = new ChunkPos(pos);
                if (!level.hasChunk(cp.x, cp.z)) {
                    continue; // unloaded before we got to it
                }
                int applied = level.getChunk(cp.x, cp.z).getData(ModAttachments.APPLIED_SLUDGE_DEPTH.get());
                if (depthNow <= applied) {
                    continue; // already at/over target
                }
                task = new Task(pos, depthNow, applied);
                CURRENT.put(dim, task);
            }

            budget = convertColumns(level, task, ceiling, budget);

            if (task.columnIndex >= 256) {
                ChunkPos cp = new ChunkPos(task.chunkPos);
                if (level.hasChunk(cp.x, cp.z)) {
                    level.getChunk(cp.x, cp.z).setData(ModAttachments.APPLIED_SLUDGE_DEPTH.get(), task.targetDepth);
                }
                CURRENT.remove(dim);
            }
        }
    }

    /** Enqueues every loaded chunk in the dimension that hasn't reached the current band depth. */
    private static void sweepLoadedChunks(ServerLevel level, ResourceKey<Level> dim, int depthNow) {
        Set<Long> loaded = LOADED.get(dim);
        if (loaded == null) {
            return;
        }
        for (long pos : loaded) {
            ChunkPos cp = new ChunkPos(pos);
            if (!level.hasChunk(cp.x, cp.z)) {
                continue;
            }
            int applied = level.getChunk(cp.x, cp.z).getData(ModAttachments.APPLIED_SLUDGE_DEPTH.get());
            if (depthNow > applied) {
                enqueue(dim, pos);
            }
        }
    }

    private static void enqueue(ResourceKey<Level> dim, long chunkPos) {
        if (QUEUED.computeIfAbsent(dim, k -> new HashSet<>()).add(chunkPos)) {
            QUEUES.computeIfAbsent(dim, k -> new ArrayDeque<>()).add(chunkPos);
        }
    }

    private static Long pollQueue(ResourceKey<Level> dim) {
        ArrayDeque<Long> queue = QUEUES.get(dim);
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        long pos = queue.poll();
        Set<Long> queued = QUEUED.get(dim);
        if (queued != null) {
            queued.remove(pos);
        }
        return pos;
    }

    private static int convertColumns(ServerLevel level, Task task, int ceiling, int budget) {
        ChunkPos cp = new ChunkPos(task.chunkPos);
        if (!level.hasChunk(cp.x, cp.z)) {
            task.columnIndex = 256; // chunk unloaded; abandon (re-queued on next load)
            return budget;
        }
        LevelChunk chunk = level.getChunk(cp.x, cp.z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = cp.getMinBlockX();
        int baseZ = cp.getMinBlockZ();
        int minY = level.getMinBuildHeight();

        while (task.columnIndex < 256) {
            if (budget <= 0) {
                return 0; // resume this column next tick (already-sludge blocks are skipped)
            }
            int lx = task.columnIndex & 15;
            int lz = (task.columnIndex >> 4) & 15;
            int worldX = baseX + lx;
            int worldZ = baseZ + lz;

            int surfaceY = topLiquidY(level, chunk, worldX, worldZ, cursor, minY);
            if (surfaceY != NO_SURFACE) {
                Band band = SludgeConversion.bandToConvert(surfaceY, ceiling, task.targetDepth, task.appliedStart);
                if (band.present()) {
                    int low = Math.max(minY, band.low());
                    for (int y = band.high(); y >= low; y--) {
                        if (budget <= 0) {
                            return 0;
                        }
                        if (convertibleToSludge(level, cursor.set(worldX, y, worldZ))) {
                            level.setBlock(
                                    cursor, ModBlocks.SLUDGE_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                            budget--;
                        }
                    }
                }
            }
            task.columnIndex++;
        }
        return budget;
    }

    /**
     * The topmost liquid Y of a column — clean water <em>or</em> already-placed sludge — which the
     * surface band anchors to, or {@link #NO_SURFACE} if the column has no liquid near its top.
     * Anchoring to the liquid surface (not the water specifically) keeps escalation re-passes correct
     * once a sludge skin is present, and scanning a few blocks from the heightmap avoids any
     * off-by-one in the surface Y so the very top block converts.
     */
    private static int topLiquidY(
            ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos cursor, int minY) {
        int from = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int to = Math.max(minY, from - SURFACE_SCAN_DEPTH);
        for (int y = from; y >= to; y--) {
            FluidState fluid = level.getFluidState(cursor.set(x, y, z));
            if (fluid.is(FluidTags.WATER) || fluid.getFluidType() == ModFluids.SLUDGE_TYPE.get()) {
                return y;
            }
        }
        return NO_SURFACE;
    }

    /**
     * True if the block at {@code pos} should become sludge: clean water, or an insubstantial
     * water-logged block (seagrass, kelp, …) — never a solid water-logged build, which is left alone.
     */
    private static boolean convertibleToSludge(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().is(FluidTags.WATER)) {
            return false; // already sludge, air, or a non-water block
        }
        return state.is(Blocks.WATER) || state.getCollisionShape(level, pos).isEmpty();
    }

    private static int currentDepth(int ceiling) {
        if (ToxicSurfaceConfig.WATER_CONVERSION_MODE.get() == WaterConversionMode.FULL) {
            return FULL_MODE_DEPTH;
        }
        return ToxicityModel.sludgeDepth(
                ceiling,
                ToxicSurfaceConfig.TOXIC_START_Y.get(),
                ToxicSurfaceConfig.ESCALATION_MAX_Y.get(),
                ToxicSurfaceConfig.SLUDGE_DEPTH_MIN.get(),
                ToxicSurfaceConfig.SLUDGE_DEPTH_MAX.get());
    }
}
