// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModDataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Industrial filter (DESIGN.md §7) — a heavy-duty, <em>reusable</em> scrubber filter for the toxic
 * generators (crafted from 4 iron + 3 clean filters). Unlike a mask/suit filter it is <b>not</b>
 * worn for breathing protection (it is never accepted by a mask or the hazmat chest); its only use
 * is the generator scrubber, where it runs the generator clean for a configurable life
 * ({@link ToxicSurfaceConfig#INDUSTRIAL_FILTER_LIFE_TICKS}, ~10–20 min) before clogging.
 *
 * <p>Its remaining clean-burn life is stored in {@link ModDataComponents#INDUSTRIAL_FILTER_LIFE}
 * and shown as a green durability bar. When it runs out the scrubber swaps it for a dirty industrial
 * filter, which is cleaned back through the wet→clean cycle (fan-washing then heat) rather than
 * thrown away — a closed loop, not a consumable stream.
 */
public class IndustrialFilterItem extends Item {
    private static final int BAR_GREEN = 0x55FF55;

    public IndustrialFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getRemaining(stack) < maxTicks();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = Math.max(1, maxTicks());
        return Mth.clamp(Math.round(13.0F * getRemaining(stack) / max), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_GREEN;
    }

    /** Configured full clean-burn lifetime of a fresh industrial filter, in ticks. */
    public static int maxTicks() {
        return ToxicSurfaceConfig.INDUSTRIAL_FILTER_LIFE_TICKS.get();
    }

    /** Remaining clean-burn ticks; a fresh filter with no stored value reads as full. */
    public static int getRemaining(ItemStack stack) {
        Integer value = stack.get(ModDataComponents.INDUSTRIAL_FILTER_LIFE.get());
        return value == null ? maxTicks() : value;
    }

    public static void setRemaining(ItemStack stack, int ticks) {
        stack.set(ModDataComponents.INDUSTRIAL_FILTER_LIFE.get(), Math.max(0, ticks));
    }
}
