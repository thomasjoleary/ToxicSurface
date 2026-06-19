// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft-backed {@link PassabilityProbe}: toxic gas can move through a cell when
 * it has no sealing collision (air or an empty-collision block).
 *
 * <p>Prototype heuristic — the precise definition of "sealing" (e.g. handling slabs,
 * trapdoors, leaves, glass panes) is tuned in-game during Phase 2. Reuses a mutable
 * cursor; single-threaded server use only.
 */
public final class LevelPassabilityProbe implements PassabilityProbe {
    private final BlockGetter level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelPassabilityProbe(BlockGetter level) {
        this.level = level;
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        BlockState state = level.getBlockState(cursor.set(x, y, z));
        return state.isAir() || state.getCollisionShape(level, cursor).isEmpty();
    }
}
