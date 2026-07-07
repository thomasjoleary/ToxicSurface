// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.menu;

import io.github.thomasjoleary.toxicsurface.block.WeaverBlockEntity;
import io.github.thomasjoleary.toxicsurface.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the Weaver (DESIGN.md §3): two input slots, a fuel slot, and an
 * output slot, plus the player inventory. The {@link ContainerData} carries the burn
 * and progress values so the screen can draw the flame and progress arrow.
 */
public class WeaverMenu extends AbstractMachineMenu {
    private final ContainerData data;

    /** Client constructor — dummy backing; the server syncs slot and data values. */
    public WeaverMenu(int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                new ItemStackHandler(WeaverBlockEntity.SLOT_COUNT),
                new SimpleContainerData(4));
    }

    /** Server constructor — bound to the block entity's inventory and data. */
    public WeaverMenu(int containerId, Inventory playerInventory, WeaverBlockEntity weaver) {
        this(containerId, playerInventory, weaver.getItems(), weaver.getDataAccess());
    }

    private WeaverMenu(int containerId, Inventory playerInventory, IItemHandler items, ContainerData data) {
        super(ModMenus.WEAVER.get(), containerId, WeaverBlockEntity.SLOT_COUNT);
        this.data = data;

        addSlot(new SlotItemHandler(items, WeaverBlockEntity.SLOT_INPUT_A, 44, 17));
        addSlot(new SlotItemHandler(items, WeaverBlockEntity.SLOT_INPUT_B, 62, 17));
        addSlot(new SlotItemHandler(items, WeaverBlockEntity.SLOT_FUEL, 53, 53));
        addSlot(new SlotItemHandler(items, WeaverBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(playerInventory, 84);
        addDataSlots(data);
    }

    /** Cooking progress as a 0..width pixel value for the arrow. */
    public int getProgressScaled(int width) {
        int progress = data.get(WeaverBlockEntity.DATA_PROGRESS);
        int max = data.get(WeaverBlockEntity.DATA_MAX_PROGRESS);
        return max > 0 && progress > 0 ? progress * width / max : 0;
    }

    /** Remaining burn as a 0..height pixel value for the flame. */
    public int getLitScaled(int height) {
        int duration = data.get(WeaverBlockEntity.DATA_LIT_DURATION);
        if (duration <= 0) {
            duration = 200;
        }
        return data.get(WeaverBlockEntity.DATA_LIT_TIME) * height / duration;
    }

    public boolean isLit() {
        return data.get(WeaverBlockEntity.DATA_LIT_TIME) > 0;
    }

    /** Routes fuel to the fuel slot and everything else to the inputs (never the output). */
    @Override
    protected boolean moveToMachine(ItemStack stack) {
        if (stack.getBurnTime(null) > 0) {
            return moveItemStackTo(stack, WeaverBlockEntity.SLOT_FUEL, WeaverBlockEntity.SLOT_FUEL + 1, false);
        }
        return moveItemStackTo(stack, WeaverBlockEntity.SLOT_INPUT_A, WeaverBlockEntity.SLOT_FUEL, false);
    }
}
