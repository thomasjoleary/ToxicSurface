// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;

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

    // Output rotation on both ends of the facing axis (the model has a shaft stub on the facing face
    // and its opposite). Without this override the KineticBlock default returns false for every face,
    // so no shaft ever connects and the generator drives nothing.
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Class<WasteGeneratorBlockEntity> getBlockEntityClass() {
        return WasteGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WasteGeneratorBlockEntity> getBlockEntityType() {
        return CreateContent.WASTE_GENERATOR_BE.get();
    }

    // No GUI — right-click with toxic fuel or an industrial filter to load it (the handler routes by
    // slot), empty-handed to take it back. Automation still goes through the item-handler capability.
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WasteGeneratorBlockEntity be) {
            return MachineHandlerInteraction.insert(be.getItemHandler(), stack, player, hand, level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WasteGeneratorBlockEntity be) {
            return MachineHandlerInteraction.extract(be.getItemHandler(), player, level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WasteGeneratorBlockEntity be) {
            IItemHandler items = be.getItemHandler();
            for (int i = 0; i < items.getSlots(); i++) {
                Block.popResource(level, pos, items.getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston); // Create's kinetic-network cleanup
    }
}
