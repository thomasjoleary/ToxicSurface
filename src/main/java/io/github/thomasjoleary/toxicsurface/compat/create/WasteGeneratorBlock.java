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
 * The Toxic Waste Generator block (DESIGN.md §7) — a Create kinetic <em>generator</em> that
 * incinerates toxic residue and compacted waste blocks to spin a shaft on the face it points
 * at. A {@link DirectionalKineticBlock} so it outputs rotation along {@code FACING};
 * {@link IBE} wires it to {@link WasteGeneratorBlockEntity}. Only registered when Create is
 * present.
 */
public class WasteGeneratorBlock extends DirectionalKineticBlock implements IBE<WasteGeneratorBlockEntity> {
    public WasteGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Output rotation on the face it points at (Create's CreativeMotor idiom). Without this override
    // the KineticBlock default returns false for every face, so no shaft ever connects and the
    // generator drives nothing.
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Class<WasteGeneratorBlockEntity> getBlockEntityClass() {
        return WasteGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WasteGeneratorBlockEntity> getBlockEntityType() {
        return CreateContent.WASTE_GENERATOR_BE.get();
    }
}
