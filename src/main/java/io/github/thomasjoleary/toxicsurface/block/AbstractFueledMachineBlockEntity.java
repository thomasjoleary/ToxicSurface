// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.compat.jade.JadeReadout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Base for the furnace-fuelled machines (Weaver, Cleanser — DESIGN.md §3): owns the item
 * handler, the shared burn bookkeeping ({@code litTime}/{@code litDuration} + fuel
 * consumption with crafting remainders), the four-entry {@link ContainerData} the screens
 * read, and the NBT round-trip for all of it. Subclasses define the slot layout via
 * {@link #isItemValid} and expose their two machine-specific data entries (progress,
 * range, …) via {@link #getMachineData}/{@link #setMachineData}.
 */
public abstract class AbstractFueledMachineBlockEntity extends BlockEntity implements MenuProvider, JadeReadout {
    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    /** ContainerData size: the two lit entries plus two machine-specific ones. */
    public static final int DATA_COUNT = 4;

    protected final ItemStackHandler items;
    protected int litTime;
    protected int litDuration;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> litTime;
                case DATA_LIT_DURATION -> litDuration;
                default -> getMachineData(index);
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> litTime = value;
                case DATA_LIT_DURATION -> litDuration = value;
                default -> setMachineData(index, value);
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected AbstractFueledMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slotCount) {
        super(type, pos, state);
        this.items = new ItemStackHandler(slotCount) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return AbstractFueledMachineBlockEntity.this.isItemValid(slot, stack);
            }
        };
    }

    /** Slot filter for hopper/menu insertion — the slot layout is machine-specific. */
    protected abstract boolean isItemValid(int slot, ItemStack stack);

    /** Machine-specific {@link ContainerData} reads (indices 2..{@link #DATA_COUNT}-1). */
    protected abstract int getMachineData(int index);

    /** Machine-specific {@link ContainerData} writes (indices 2..{@link #DATA_COUNT}-1). */
    protected abstract void setMachineData(int index, int value);

    public IItemHandler getItemHandler() {
        return items;
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public ContainerData getDataAccess() {
        return data;
    }

    /**
     * Lights the next unit of fuel from {@code fuelSlot}: sets the lit timers and leaves
     * any crafting remainder (e.g. an empty bucket) behind. Returns {@code false} when
     * the slot holds nothing burnable.
     */
    protected boolean consumeFuel(int fuelSlot) {
        ItemStack fuel = items.getStackInSlot(fuelSlot);
        int burn = fuel.getBurnTime(null);
        if (burn <= 0) {
            return false;
        }
        litTime = burn;
        litDuration = burn;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !remainder.isEmpty()) {
            items.setStackInSlot(fuelSlot, remainder);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
    }
}
