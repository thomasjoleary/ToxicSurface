// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.core.equipment.MaskFilter;
import io.github.thomasjoleary.toxicsurface.item.FaceMaskItem;
import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Non-consuming check for whether a player currently has active gas protection — a hazmat suit
 * core with filter charge, or a worn face mask with an active filter (DESIGN.md §3). Unlike the
 * air-bar logic in {@link GasEffectHandler} this neither burns filters nor steps the bar; it just
 * reports protection, so transient hazards (e.g. the sludge fan airflow) can honour the same
 * mask/suit rules as standing in the gas.
 */
public final class GasProtection {
    private GasProtection() {}

    public static boolean isProtected(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (HazmatSuit.hasSuitCore(player) && HazmatSuit.usableFilterCount(chest) > 0) {
            return true;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof FaceMaskItem && MaskFilter.isActive(FaceMaskItem.remaining(head));
    }
}
