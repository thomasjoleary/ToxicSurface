// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The exhaust scrubber shared by both toxic generators (DESIGN.md §7). A generator can run
 * <em>clean</em> — venting no smog and adding no pollution — only while it is burning a clean (or
 * carbon) air filter in its scrubber slot; this ties generator exhaust into the existing filter
 * economy (DESIGN.md §3 Filters & masks) and makes "run cleanly" an ongoing filter cost, not a
 * free switch. A spent filter is ejected as a plain {@link ModItems#USED_AIR_FILTER used filter}
 * for the wash loop (carbon degrades to a plain used filter, matching the mask behaviour). With
 * no filter the generator runs raw and the smog/pollution drawbacks apply in full.
 *
 * <p>Free of any Create types so it compiles and runs in the standalone jar; the per-machine
 * scrubber countdown is owned by the block entity and threaded through {@link #advance}.
 */
public final class ExhaustScrubber {
    private ExhaustScrubber() {}

    /** True if {@code stack} is a filter the scrubber will burn (clean or carbon). */
    public static boolean isFilter(ItemStack stack) {
        return stack.is(ModItems.CLEAN_AIR_FILTER.get()) || stack.is(ModItems.CARBON_AIR_FILTER.get());
    }

    /**
     * Advances the scrubber by one running tick and returns the new countdown. A positive return
     * means this tick's exhaust was captured (run clean); zero means no filter, so the caller must
     * vent. Spends/ejects filters as needed, consuming from {@code items[filterSlot]} and popping a
     * used filter at {@code ejectPos} when one is exhausted.
     *
     * @param scrubTicks the remaining clean-burn ticks on the filter currently loaded (0 if none)
     */
    public static int advance(Level level, BlockPos ejectPos, ItemStackHandler items, int filterSlot, int scrubTicks) {
        if (scrubTicks > 0) {
            scrubTicks--;
            if (scrubTicks > 0) {
                return scrubTicks; // current filter still has charge
            }
            // The loaded filter just spent out — eject it for the wash loop, then load the next.
            Block.popResource(level, ejectPos, new ItemStack(ModItems.USED_AIR_FILTER.get()));
        }

        ItemStack filter = items.getStackInSlot(filterSlot);
        boolean carbon = filter.is(ModItems.CARBON_AIR_FILTER.get());
        if (carbon || filter.is(ModItems.CLEAN_AIR_FILTER.get())) {
            int life = ToxicSurfaceConfig.MASK_DURATION_TICKS.get();
            if (carbon) {
                life = Math.max(1, (int) Math.round(life * ToxicSurfaceConfig.CARBON_FILTER_DURATION_MULTIPLIER.get()));
            }
            filter.shrink(1);
            return life; // fresh filter loaded; this tick runs clean
        }
        return 0; // no filter available: vent raw exhaust
    }
}
