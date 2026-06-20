// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * Shared filter-tier helpers (DESIGN.md §3 Filters & masks). A plain clean filter and a
 * carbon (activated) filter are both usable; carbon lasts {@code
 * carbonFilterDurationMultiplier}× longer. Both degrade to a plain used filter when spent.
 */
public final class AirFilter {
    private AirFilter() {}

    /** A clean filter that can be installed/burned (plain or carbon). */
    public static boolean isClean(ItemStack stack) {
        return stack.is(ModItems.CLEAN_AIR_FILTER.get()) || stack.is(ModItems.CARBON_AIR_FILTER.get());
    }

    public static boolean isCarbon(ItemStack stack) {
        return stack.is(ModItems.CARBON_AIR_FILTER.get());
    }

    /** Full lifetime in ticks for the given clean filter, based on the plain-filter config. */
    public static int lifetimeTicks(ItemStack stack) {
        int base = ToxicSurfaceConfig.MASK_DURATION_TICKS.get();
        return isCarbon(stack)
                ? (int) Math.round(base * ToxicSurfaceConfig.CARBON_FILTER_DURATION_MULTIPLIER.get())
                : base;
    }
}
