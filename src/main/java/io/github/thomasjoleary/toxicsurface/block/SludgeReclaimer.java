// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import io.github.thomasjoleary.toxicsurface.world.CleanserVisual;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Shared sludge-reversion sweep for both Cleanser variants (DESIGN.md §3). The fuel
 * {@link CleanserBlockEntity} and the Create-powered Mechanical Cleanser run the identical
 * reclamation pass — a budgeted sphere scan that turns sludge back into water — so the logic
 * lives here once. Kept free of any Create types so it compiles and runs in the standalone jar.
 */
public final class SludgeReclaimer {
    /** Base cells scanned per active tick for the reversion sweep (bounds the work). */
    public static final int SCAN_BUDGET = 4096;

    /** Aim to sweep the whole sphere within this many ticks regardless of radius. */
    private static final int TARGET_SWEEP_TICKS = 40;
    /** Per-tick cell-visit cap so a huge radius can't stall the server thread. */
    private static final int MAX_SCAN_BUDGET = 65_536;

    private SludgeReclaimer() {}

    /** True when there is anything to reclaim: a server level in an affected, already-toxic dimension. */
    public static boolean canReclaim(Level level) {
        return level instanceof ServerLevel sl
                && ToxicityTicker.isAffected(sl)
                && ToxicityTicker.currentToxicY(sl) != ToxicityTicker.NOT_TOXIC;
    }

    /**
     * One active tick of the full cleanser effect both variants project: the budgeted
     * sludge-reversion sweep, the breathable-air bubble, and the green clean-air dome
     * particles. Returns the updated scan cursor to resume from next tick.
     */
    public static int tickActive(ServerLevel level, BlockPos pos, int range, int scanCursor) {
        scanCursor = revertSludge(level, pos, range, SCAN_BUDGET, scanCursor);
        CleanserBubbles.update(level, pos, range); // keep breathable air in range
        CleanserVisual.tick(level, pos, range); // green clean-air dome particles
        return scanCursor;
    }

    /**
     * Sweeps a budgeted window of the sphere around {@code pos}, turning sludge into water, and
     * returns the cursor to resume from next tick. The scan wraps around so the whole sphere is
     * covered over successive ticks while each tick's work stays bounded.
     *
     * <p>Two fixes vs. a naive fixed-budget scan (DESIGN.md §3): (1) the per-tick budget scales with
     * the sphere volume (clamped to {@link #MAX_SCAN_BUDGET}) so a large radius is still swept in
     * roughly {@link #TARGET_SWEEP_TICKS} instead of minutes; (2) <b>unloaded chunks are skipped</b>
     * (via {@code hasChunk}, cached per chunk column) instead of force-loaded — {@code getBlockState}
     * would otherwise synchronously generate every distant chunk every tick and edit untracked chunks
     * the client never sees, which made far sludge only clean up once you walked over to it.
     */
    public static int revertSludge(Level level, BlockPos pos, int range, int scanBudget, int scanCursor) {
        int side = 2 * range + 1;
        long total = (long) side * side * side;
        if (scanCursor >= total) {
            scanCursor = 0;
        }
        int budget = (int) Math.max(scanBudget, Math.min(MAX_SCAN_BUDGET, total / TARGET_SWEEP_TICKS));
        int rangeSq = range * range;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean chunkLoaded = false;
        for (int n = 0; n < budget; n++) {
            if (scanCursor >= total) {
                scanCursor = 0;
            }
            int idx = scanCursor++;
            int dx = idx % side - range;
            int dy = (idx / side) % side - range;
            int dz = idx / (side * side) - range;
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }
            int worldX = pos.getX() + dx;
            int worldZ = pos.getZ() + dz;
            int chunkX = worldX >> 4;
            int chunkZ = worldZ >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
                chunkLoaded = level.getChunkSource().hasChunk(chunkX, chunkZ); // no force-load
            }
            if (!chunkLoaded) {
                continue; // leave far/unloaded sludge until its chunk is actually loaded
            }
            cursor.set(worldX, pos.getY() + dy, worldZ);
            if (level.getBlockState(cursor).is(ModBlocks.SLUDGE_BLOCK.get())) {
                level.setBlock(cursor, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return scanCursor;
    }
}
