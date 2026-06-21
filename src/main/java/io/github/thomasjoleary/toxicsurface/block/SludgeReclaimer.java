// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
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
    private SludgeReclaimer() {}

    /** True when there is anything to reclaim: a server level in an affected, already-toxic dimension. */
    public static boolean canReclaim(Level level) {
        return level instanceof ServerLevel sl
                && ToxicityTicker.isAffected(sl)
                && ToxicityTicker.currentToxicY(sl) != ToxicityTicker.NOT_TOXIC;
    }

    /**
     * Sweeps a budgeted window of the sphere around {@code pos}, turning sludge into water, and
     * returns the cursor to resume from next tick. The scan wraps around so the whole sphere is
     * covered over successive ticks while each tick's work stays bounded by {@code scanBudget}.
     */
    public static int revertSludge(Level level, BlockPos pos, int range, int scanBudget, int scanCursor) {
        int side = 2 * range + 1;
        long total = (long) side * side * side;
        if (scanCursor >= total) {
            scanCursor = 0;
        }
        int rangeSq = range * range;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int n = 0; n < scanBudget; n++) {
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
            cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            if (level.getBlockState(cursor).is(ModBlocks.SLUDGE_BLOCK.get())) {
                level.setBlock(cursor, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return scanCursor;
    }
}
