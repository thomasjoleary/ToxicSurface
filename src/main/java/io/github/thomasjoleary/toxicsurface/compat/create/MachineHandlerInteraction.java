// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Shared right-click insert/extract for the Create machines that have no GUI (the generators' fuel and
 * scrubber-filter slots). Insertion is routed by the handler's own {@code isItemValid}, so a held item
 * lands in whatever slot accepts it; extraction pulls from the highest-index non-empty slot first.
 */
final class MachineHandlerInteraction {
    private MachineHandlerInteraction() {}

    /** Right-click with an item: insert as much of the held stack as fits across the handler's slots. */
    static ItemInteractionResult insert(
            IItemHandler items, ItemStack held, Player player, InteractionHand hand, boolean clientSide) {
        if (held.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Simulate across every slot to see whether any of it fits before committing.
        ItemStack remaining = held;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            remaining = items.insertItem(slot, remaining, true);
        }
        if (remaining.getCount() == held.getCount()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // nothing fits
        }
        if (!clientSide) {
            ItemStack working = held;
            for (int slot = 0; slot < items.getSlots(); slot++) {
                working = items.insertItem(slot, working, false);
            }
            player.setItemInHand(hand, working);
        }
        return ItemInteractionResult.sidedSuccess(clientSide);
    }

    /** Empty-hand right-click: take the contents of the highest-index non-empty slot back. */
    static InteractionResult extract(IItemHandler items, Player player, boolean clientSide) {
        for (int slot = items.getSlots() - 1; slot >= 0; slot--) {
            ItemStack taken = items.extractItem(slot, items.getStackInSlot(slot).getMaxStackSize(), clientSide);
            if (!taken.isEmpty()) {
                if (!clientSide && !player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
                return InteractionResult.sidedSuccess(clientSide);
            }
        }
        return InteractionResult.PASS;
    }
}
