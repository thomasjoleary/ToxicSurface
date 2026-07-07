// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.menu.WeaverMenu;
import io.github.thomasjoleary.toxicsurface.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Weaver (DESIGN.md §3) — a furnace-fuelled textile/filtration fabricator. Two
 * input slots + fuel + output; converts fibre into Hazmat Material and air filters
 * via a small hard-coded recipe table (datapack-driven recipes are a future
 * enhancement). A redstone signal halts it; the item handler is hopper-automatable.
 */
public class WeaverBlockEntity extends AbstractFueledMachineBlockEntity {
    public static final int SLOT_INPUT_A = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_FUEL = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_PROGRESS = 2;
    public static final int DATA_MAX_PROGRESS = 3;

    private int progress;
    private int maxProgress;

    public WeaverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEAVER.get(), pos, state, SLOT_COUNT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FUEL -> stack.getBurnTime(null) > 0;
            case SLOT_OUTPUT -> false;
            default -> true;
        };
    }

    @Override
    protected int getMachineData(int index) {
        return switch (index) {
            case DATA_PROGRESS -> progress;
            case DATA_MAX_PROGRESS -> maxProgress;
            default -> 0;
        };
    }

    @Override
    protected void setMachineData(int index, int value) {
        switch (index) {
            case DATA_PROGRESS -> progress = value;
            case DATA_MAX_PROGRESS -> maxProgress = value;
            default -> {}
        }
    }

    @Override
    public void appendJadeData(CompoundTag tag) {
        tag.putBoolean("tsActive", litTime > 0);
        if (maxProgress > 0 && progress > 0) {
            tag.putInt("tsWeave", Math.min(100, progress * 100 / maxProgress));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.toxicsurface.weaver");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WeaverMenu(containerId, playerInventory, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WeaverBlockEntity be) {
        boolean changed = false;
        if (be.litTime > 0) {
            be.litTime--;
        }

        WeaverLogic.WeaveRecipe recipe = level.hasNeighborSignal(pos)
                ? null
                : WeaverLogic.find(be.items.getStackInSlot(SLOT_INPUT_A), be.items.getStackInSlot(SLOT_INPUT_B));
        if (recipe != null && WeaverLogic.canOutput(be.items, SLOT_OUTPUT, recipe.result())) {
            if (be.litTime <= 0) {
                changed |= be.consumeFuel(SLOT_FUEL);
            }
            if (be.litTime > 0) {
                be.maxProgress = recipe.time();
                be.progress++;
                if (be.progress >= be.maxProgress) {
                    WeaverLogic.craft(be.items, SLOT_INPUT_A, SLOT_INPUT_B, SLOT_OUTPUT, recipe);
                    be.progress = 0;
                }
                changed = true;
            } else if (be.progress != 0) {
                be.progress = 0;
                changed = true;
            }
        } else if (be.progress != 0) {
            be.progress = 0;
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
    }
}
