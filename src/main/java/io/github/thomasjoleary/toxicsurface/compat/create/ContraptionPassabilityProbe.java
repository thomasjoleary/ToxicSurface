// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import io.github.thomasjoleary.toxicsurface.core.enclosure.PassabilityProbe;
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
 * {@link PassabilityProbe} backed by a Create {@link Contraption}'s captured blocks rather than the
 * world, so the enclosure flood-fill can seal rooms inside a moving contraption (DESIGN.md §9). Cells
 * are addressed in the contraption's local block grid (the keys of {@link Contraption#getBlocks()}).
 * Mirrors {@code LevelPassabilityProbe}'s rules, but reads collision shapes against an empty getter
 * since the structure has no surrounding world at these positions. Loaded only with Create.
 */
public final class ContraptionPassabilityProbe implements PassabilityProbe {
    private final Contraption contraption;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public ContraptionPassabilityProbe(Contraption contraption) {
        this.contraption = contraption;
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        StructureBlockInfo info = contraption.getBlocks().get(cursor.set(x, y, z));
        if (info == null) {
            return true; // no contraption block here — open (air, or outside the structure)
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
