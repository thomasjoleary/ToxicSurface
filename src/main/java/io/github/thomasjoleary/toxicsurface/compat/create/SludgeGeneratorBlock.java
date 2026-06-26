// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Toxic Sludge Generator block (DESIGN.md §7) — a Create kinetic <em>generator</em> that
 * burns toxic sludge from an internal tank to spin a shaft on the face it points at. A
 * {@link DirectionalKineticBlock} so it outputs rotation along {@code FACING}; {@link IBE} wires
 * it to {@link SludgeGeneratorBlockEntity}. Create pumps/pipes fill its tank (the sludge is a
 * real NeoForge fluid). Only registered when Create is present.
 */
public class SludgeGeneratorBlock extends DirectionalKineticBlock implements IBE<SludgeGeneratorBlockEntity> {
    public SludgeGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Output rotation on both ends of the facing axis (the model has a shaft stub on the facing face
    // and its opposite). Without this override the KineticBlock default returns false for every face,
    // so no shaft ever connects and the generator drives nothing.
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Class<SludgeGeneratorBlockEntity> getBlockEntityClass() {
        return SludgeGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SludgeGeneratorBlockEntity> getBlockEntityType() {
        return CreateContent.SLUDGE_GENERATOR_BE.get();
    }
}
