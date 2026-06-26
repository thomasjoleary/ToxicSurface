// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Minecraft-backed {@link PassabilityProbe}: toxic gas can move through a cell when it has no sealing
 * barrier (air or an empty-collision block), with a few stateful blocks handled explicitly because
 * their collision shape doesn't reflect whether they seal an opening:
 *
 * <ul>
 *   <li><b>Doors / trapdoors / fence gates</b> seal only when <em>closed</em> — an open one leaves a
 *       gap (its thin side-collision would otherwise be read as sealing).
 *   <li><b>Pistons</b>: a retracted piston is a normal full block and seals; an <em>extended</em>
 *       piston base, its head, and moving-piston blocks leave gaps around the arm and don't seal.
 * </ul>
 *
 * <p>Reuses a mutable cursor; single-threaded server use only.
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
        if (state.isAir()) {
            return true;
        }
        // Open doors/trapdoors/fence gates don't seal; closed ones do.
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return state.getValue(BlockStateProperties.OPEN);
        }
        // Extended pistons leave gaps around the arm; a retracted base is just a full block.
        if (state.getBlock() instanceof PistonBaseBlock) {
            return state.getValue(BlockStateProperties.EXTENDED);
        }
        if (state.getBlock() instanceof PistonHeadBlock || state.getBlock() instanceof MovingPistonBlock) {
            return true;
        }
        return state.getCollisionShape(level, cursor).isEmpty();
    }
}
