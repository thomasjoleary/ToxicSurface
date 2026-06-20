// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.menu;

import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Filter inventory for the hazmat chestpiece (DESIGN.md §3): {@link HazmatSuit#CAPACITY}
 * slots that accept clean/used air filters, plus the player inventory. Loaded from the
 * chest's container component on open and written back on close.
 */
public class HazmatChestMenu extends AbstractContainerMenu {
    private static final int FILTER_SLOTS = HazmatSuit.CAPACITY;

    private final SimpleContainer filters;
    private final ItemStack chest; // server: the actual chest stack; client: EMPTY

    /** Client constructor (empty container; the server syncs slot contents). */
    public HazmatChestMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(FILTER_SLOTS), ItemStack.EMPTY);
    }

    /** Server constructor — loads the chest's filters. */
    public HazmatChestMenu(int containerId, Inventory playerInventory, ItemStack chest) {
        this(containerId, playerInventory, HazmatSuit.loadFilters(chest), chest);
    }

    private HazmatChestMenu(int containerId, Inventory playerInventory, SimpleContainer filters, ItemStack chest) {
        super(ModMenus.HAZMAT_CHEST.get(), containerId);
        this.filters = filters;
        this.chest = chest;

        for (int i = 0; i < FILTER_SLOTS; i++) {
            addSlot(new FilterSlot(filters, i, 8 + i * 18, 20));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 109));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!chest.isEmpty()) {
            HazmatSuit.saveFilters(chest, filters);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int invStart = FILTER_SLOTS;
            int invEnd = invStart + 36;
            if (index < invStart) {
                if (!moveItemStackTo(stack, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, invStart, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    /** A slot that accepts only air filters, one per slot. */
    private static final class FilterSlot extends Slot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.CLEAN_AIR_FILTER.get()) || stack.is(ModItems.USED_AIR_FILTER.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
