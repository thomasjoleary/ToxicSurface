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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Mechanical Weaver block (DESIGN.md §3). A {@link DirectionalKineticBlock} driven by a
 * rotation shaft on the face it points at; {@link IBE} wires it to
 * {@link MechanicalWeaverBlockEntity}. Only registered when Create is present.
 *
 * <p>{@link #WORK_FACE} is the face where items rest and the weaving rods animate — always
 * perpendicular to {@link #FACING}. For horizontal shaft orientations it is always {@link Direction#UP};
 * for vertical shaft orientations it is set to the player's horizontal facing direction at placement
 * time. The block model uses a distinct texture on this face so it is immediately recognisable.
 */
public class MechanicalWeaverBlock extends DirectionalKineticBlock implements IBE<MechanicalWeaverBlockEntity> {
    /**
     * The face where items rest and rods animate — always perpendicular to {@link #FACING}.
     * {@code UP} when the shaft runs horizontally; one of the four horizontal directions when the
     * shaft runs vertically.
     */
    public static final DirectionProperty WORK_FACE = DirectionProperty.create("work_face");

    public MechanicalWeaverBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORK_FACE, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); // adds FACING
        builder.add(WORK_FACE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context); // sets FACING
        Direction facing = state.getValue(FACING);
        // Horizontal shaft → work face is always UP; vertical shaft → set to the horizontal
        // direction the player is facing so they can aim the work surface where they need it.
        Direction workFace = facing.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection() : Direction.UP;
        return state.setValue(WORK_FACE, workFace);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Accept drive on both ends of the facing axis (a shaft on either the facing face or its
    // opposite). Without this override the KineticBlock default returns false for every face, so no
    // shaft connects and the machine never receives rotation.
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Class<MechanicalWeaverBlockEntity> getBlockEntityClass() {
        return MechanicalWeaverBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalWeaverBlockEntity> getBlockEntityType() {
        return CreateContent.MECHANICAL_WEAVER_BE.get();
    }

    // The machine has no GUI — items live on the depot-style top face. Right-click with an item to
    // drop it onto the inputs, empty-handed to take the result (or an input) back. Automation still
    // goes through the exposed item handler capability.
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof MechanicalWeaverBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        IItemHandler items = be.getItemHandler();
        // Does any of the held stack fit in an input slot? (Simulate across both inputs first.)
        ItemStack leftover = stack;
        for (int slot = MechanicalWeaverBlockEntity.SLOT_INPUT_A;
                slot <= MechanicalWeaverBlockEntity.SLOT_INPUT_B;
                slot++) {
            leftover = items.insertItem(slot, leftover, true);
        }
        if (leftover.getCount() == stack.getCount()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // nothing fits
        }
        if (!level.isClientSide) {
            ItemStack working = stack;
            for (int slot = MechanicalWeaverBlockEntity.SLOT_INPUT_A;
                    slot <= MechanicalWeaverBlockEntity.SLOT_INPUT_B;
                    slot++) {
                working = items.insertItem(slot, working, false);
            }
            player.setItemInHand(hand, working);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof MechanicalWeaverBlockEntity be)) {
            return InteractionResult.PASS;
        }
        IItemHandler items = be.getItemHandler();
        // Take the finished output first, then an input if there's no output yet.
        for (int slot : new int[] {
            MechanicalWeaverBlockEntity.SLOT_OUTPUT,
            MechanicalWeaverBlockEntity.SLOT_INPUT_B,
            MechanicalWeaverBlockEntity.SLOT_INPUT_A
        }) {
            ItemStack taken = items.extractItem(slot, items.getStackInSlot(slot).getMaxStackSize(), level.isClientSide);
            if (!taken.isEmpty()) {
                if (!level.isClientSide && !player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MechanicalWeaverBlockEntity be) {
            IItemHandler items = be.getItemHandler();
            for (int i = 0; i < items.getSlots(); i++) {
                Block.popResource(level, pos, items.getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston); // Create's kinetic-network cleanup
    }
}
