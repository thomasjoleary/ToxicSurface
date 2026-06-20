// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.registry.ModDataComponents;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Helpers for the hazmat chestpiece's filter store and full-suit checks
 * (DESIGN.md §3 Hazmat suit).
 */
public final class HazmatSuit {
    private HazmatSuit() {}

    public static boolean isChestpiece(ItemStack stack) {
        return stack.is(ModItems.HAZMAT_CHESTPLATE.get());
    }

    public static SuitData data(ItemStack chest) {
        return chest.get(ModDataComponents.SUIT_DATA.get());
    }

    public static void setData(ItemStack chest, SuitData data) {
        chest.set(ModDataComponents.SUIT_DATA.get(), data);
    }

    /** Whole filters currently loaded in the chest (0 if none / not a chestpiece). */
    public static int filterCount(ItemStack chest) {
        SuitData data = data(chest);
        return data == null ? 0 : data.filters();
    }

    /** True while the chest still has filter charge to protect the wearer. */
    public static boolean isProtecting(ItemStack chest) {
        return isChestpiece(chest) && filterCount(chest) > 0;
    }

    /**
     * Gas protection requires <em>both</em> the hazmat helmet and chestpiece worn
     * (DESIGN.md §3): the helmet seals the breathing path, the chest holds the filters.
     */
    public static boolean hasSuitCore(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.HAZMAT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.HAZMAT_CHESTPLATE.get());
    }

    /** True when all four hazmat pieces are worn — required for sludge-contact immunity. */
    public static boolean isFullSuit(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.HAZMAT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.HAZMAT_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.HAZMAT_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.HAZMAT_BOOTS.get());
    }
}
