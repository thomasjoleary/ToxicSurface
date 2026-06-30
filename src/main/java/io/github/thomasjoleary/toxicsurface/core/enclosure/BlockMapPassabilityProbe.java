// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * {@link PassabilityProbe} over a captured block map (the {@code Map<BlockPos, StructureBlockInfo>}
 * keyed by local position that a Create contraption exposes via {@code getBlocks()}), so the enclosure
 * flood-fill can seal rooms inside a moving contraption (DESIGN.md §9). Mirrors
 * {@link LevelPassabilityProbe}'s door/piston/collision rules, but reads collision shapes against an
 * empty block getter since the captured structure has no surrounding world at these positions.
 *
 * <p>Deliberately free of any Create type (the map values are vanilla {@code StructureBlockInfo}), so
 * the sealing rules can be exercised by the standalone GameTests with a hand-built block map.
 */
public final class BlockMapPassabilityProbe implements PassabilityProbe {
    private final Map<BlockPos, StructureBlockInfo> blocks;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public BlockMapPassabilityProbe(Map<BlockPos, StructureBlockInfo> blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        StructureBlockInfo info = blocks.get(cursor.set(x, y, z));
        if (info == null) {
            return true; // no captured block here — open (air, or outside the structure)
        }
        BlockState state = info.state();
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
        return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }
}
