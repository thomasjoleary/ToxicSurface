// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModDataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Face mask (DESIGN.md §3 Filters & masks). A plain item that may be worn in the
 * helmet slot (via {@link #canEquip}); its {@link MaskData} filter time is shown as a
 * green durability bar. Filter consumption and gas protection are driven by the gas
 * effect handler.
 */
public class FaceMaskItem extends Item {
    private static final int BAR_GREEN = 0x55FF55;

    public FaceMaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
        return armorType == EquipmentSlot.HEAD;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = Math.max(1, ToxicSurfaceConfig.MASK_DURATION_TICKS.get());
        return Mth.clamp(Math.round(13.0F * remaining(stack) / max), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_GREEN;
    }

    public static int remaining(ItemStack stack) {
        MaskData data = stack.get(ModDataComponents.MASK_DATA.get());
        return data == null ? 0 : data.remainingTicks();
    }

    public static void setRemaining(ItemStack stack, int ticks) {
        stack.set(ModDataComponents.MASK_DATA.get(), new MaskData(Math.max(0, ticks)));
    }

    public static boolean hasActiveFilter(ItemStack stack) {
        return remaining(stack) > 0;
    }
}
