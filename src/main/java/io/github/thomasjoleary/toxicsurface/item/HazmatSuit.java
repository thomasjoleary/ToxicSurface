// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.registry.ModDataComponents;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Helpers for the hazmat chestpiece's filter inventory and full-suit checks
 * (DESIGN.md §3 Hazmat suit). Filters are stored as real items in the chest's
 * {@code minecraft:container} component (one filter per slot) so they can be swapped
 * via the chest's inventory screen; the burning filter's remaining time is the
 * {@link SuitData} component.
 */
public final class HazmatSuit {
    /** Filter slots on the chest (DESIGN.md §3 — up to 10 filters). */
    public static final int CAPACITY = 10;

    private HazmatSuit() {}

    public static boolean isChestpiece(ItemStack stack) {
        return stack.is(ModItems.HAZMAT_CHESTPLATE.get());
    }

    /** Loads the chest's filter container into a {@link SimpleContainer}. */
    public static SimpleContainer loadFilters(ItemStack chest) {
        NonNullList<ItemStack> items = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
        chest.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .copyInto(items);
        return new SimpleContainer(items.toArray(ItemStack[]::new));
    }

    /** Writes a filter container back into the chest's container component. */
    public static void saveFilters(ItemStack chest, SimpleContainer container) {
        NonNullList<ItemStack> items = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(CAPACITY, container.getContainerSize()); i++) {
            items.set(i, container.getItem(i));
        }
        chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    /** Number of usable filters (plain or carbon) currently loaded in the chest. */
    public static int usableFilterCount(ItemStack chest) {
        int count = 0;
        SimpleContainer container = loadFilters(chest);
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (AirFilter.isClean(container.getItem(i))) {
                count++;
            }
        }
        return count;
    }

    /** Full lifetime in ticks of the next filter that would be burned, or {@code 0} if none. */
    public static int nextFilterLifetime(ItemStack chest) {
        SimpleContainer container = loadFilters(chest);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (AirFilter.isClean(stack)) {
                return AirFilter.lifetimeTicks(stack);
            }
        }
        return 0;
    }

    /** Burns the next usable filter (turns it into a plain used filter in place). Returns true if one was burned. */
    public static boolean burnOneFilter(ItemStack chest) {
        SimpleContainer container = loadFilters(chest);
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (AirFilter.isClean(container.getItem(i))) {
                container.setItem(i, new ItemStack(ModItems.USED_AIR_FILTER.get()));
                saveFilters(chest, container);
                return true;
            }
        }
        return false;
    }

    public static int activeTicks(ItemStack chest) {
        return chest.getOrDefault(ModDataComponents.SUIT_DATA.get(), SuitData.EMPTY)
                .activeTicks();
    }

    public static void setActiveTicks(ItemStack chest, int ticks) {
        chest.set(ModDataComponents.SUIT_DATA.get(), new SuitData(Math.max(0, ticks)));
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
