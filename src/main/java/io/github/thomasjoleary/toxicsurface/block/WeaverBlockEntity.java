// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.menu.WeaverMenu;
import io.github.thomasjoleary.toxicsurface.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Weaver (DESIGN.md §3) — a furnace-fuelled textile/filtration fabricator. Two
 * input slots + fuel + output; converts fibre into Hazmat Material and air filters
 * via a small hard-coded recipe table (datapack-driven recipes are a future
 * enhancement). A redstone signal halts it; the item handler is hopper-automatable.
 */
public class WeaverBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_INPUT_A = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_FUEL = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_MAX_PROGRESS = 3;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_FUEL -> stack.getBurnTime(null) > 0;
                case SLOT_OUTPUT -> false;
                default -> true;
            };
        }
    };

    private int litTime;
    private int litDuration;
    private int progress;
    private int maxProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> litTime;
                case DATA_LIT_DURATION -> litDuration;
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> litTime = value;
                case DATA_LIT_DURATION -> litDuration = value;
                case DATA_PROGRESS -> progress = value;
                case DATA_MAX_PROGRESS -> maxProgress = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public WeaverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEAVER.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return items;
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public ContainerData getDataAccess() {
        return data;
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
        if (recipe != null && be.canOutput(recipe.result())) {
            if (be.litTime <= 0) {
                changed |= be.consumeFuel();
            }
            if (be.litTime > 0) {
                be.maxProgress = recipe.time();
                be.progress++;
                if (be.progress >= be.maxProgress) {
                    be.craft(recipe);
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

    private boolean consumeFuel() {
        ItemStack fuel = items.getStackInSlot(SLOT_FUEL);
        int burn = fuel.getBurnTime(null);
        if (burn <= 0) {
            return false;
        }
        litTime = burn;
        litDuration = burn;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !remainder.isEmpty()) {
            items.setStackInSlot(SLOT_FUEL, remainder);
        }
        return true;
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    private void craft(WeaverLogic.WeaveRecipe recipe) {
        recipe.consume(items.getStackInSlot(SLOT_INPUT_A), items.getStackInSlot(SLOT_INPUT_B));
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, recipe.result().copy());
        } else {
            out.grow(recipe.result().getCount());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
    }
}
