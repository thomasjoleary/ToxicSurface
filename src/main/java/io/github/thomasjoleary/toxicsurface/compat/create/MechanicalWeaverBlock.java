// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mechanical Weaver block (DESIGN.md §3). A {@link DirectionalKineticBlock} driven by a
 * rotation shaft on the face it points at; {@link IBE} wires it to
 * {@link MechanicalWeaverBlockEntity}. Only registered when Create is present.
 */
public class MechanicalWeaverBlock extends DirectionalKineticBlock implements IBE<MechanicalWeaverBlockEntity> {
    public MechanicalWeaverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public Class<MechanicalWeaverBlockEntity> getBlockEntityClass() {
        return MechanicalWeaverBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalWeaverBlockEntity> getBlockEntityType() {
        return CreateContent.MECHANICAL_WEAVER_BE.get();
    }
}
