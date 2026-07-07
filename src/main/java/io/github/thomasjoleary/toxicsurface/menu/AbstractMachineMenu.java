// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Base for the mod's machine menus: every one is "machine slots first, then the standard
 * 27+9 player inventory", so the inventory layout and the machine↔inventory half of
 * shift-clicking live here once. Subclasses add their machine slots, then call
 * {@link #addPlayerInventory}; {@link #moveToMachine} routes inventory→machine moves and
 * is overridden where a machine cares which slot an item lands in (e.g. fuel vs. inputs).
 */
abstract class AbstractMachineMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_SIZE = 36;

    /** Machine slot count — the player inventory occupies the indices after these. */
    private final int machineSlots;

    protected AbstractMachineMenu(MenuType<?> type, int containerId, int machineSlots) {
        super(type, containerId);
        this.machineSlots = machineSlots;
    }

    /** Adds the standard 3×9 grid + hotbar, with the grid's top row at {@code yTop}. */
    protected void addPlayerInventory(Inventory playerInventory, int yTop) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, yTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, yTop + 58));
        }
    }

    /**
     * Moves a shift-clicked player-inventory stack into the machine. The default tries
     * every machine slot in order (slot validity still applies); override to route by
     * item type instead.
     */
    protected boolean moveToMachine(ItemStack stack) {
        return moveItemStackTo(stack, 0, machineSlots, false);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < machineSlots) {
                if (!moveItemStackTo(stack, machineSlots, machineSlots + PLAYER_INV_SIZE, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveToMachine(stack)) {
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
}
