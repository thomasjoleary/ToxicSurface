// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mechanical Cleanser block (DESIGN.md §3 "Create variant"). A {@link DirectionalKineticBlock}
 * so a rotation shaft drives it from the face it points at; {@link IBE} wires it to
 * {@link MechanicalCleanserBlockEntity}. Only registered when Create is present, so the
 * standalone jar never references Create's kinetic types.
 */
public class MechanicalCleanserBlock extends DirectionalKineticBlock implements IBE<MechanicalCleanserBlockEntity> {
    public MechanicalCleanserBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public Class<MechanicalCleanserBlockEntity> getBlockEntityClass() {
        return MechanicalCleanserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalCleanserBlockEntity> getBlockEntityType() {
        return CreateContent.MECHANICAL_CLEANSER_BE.get();
    }
}
