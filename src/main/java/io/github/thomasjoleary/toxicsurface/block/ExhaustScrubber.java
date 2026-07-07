// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.item.IndustrialFilterItem;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.world.GeneratorEmissions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The exhaust scrubber shared by both toxic generators (DESIGN.md §7). A generator runs
 * <em>clean</em> — venting no smog and adding no pollution — only while a clean
 * {@link ModItems#INDUSTRIAL_FILTER industrial filter} is loaded in its scrubber slot. Industrial
 * filters are the <b>only</b> filter the generators accept (normal mask/carbon filters are not),
 * and they are reusable: each running tick spends one tick of the filter's clean-burn life, and
 * when that life runs out the filter is converted in place to a
 * {@link ModItems#DIRTY_INDUSTRIAL_FILTER dirty industrial filter} — after which the generator
 * vents raw until a clean one is swapped in. The dirty filter is then restored through its own
 * cleaning cycle (fan-wash to wet, then heat back to clean), so this is a closed loop rather than a
 * stream of disposable filters.
 *
 * <p>Free of any Create types so it compiles and runs in the standalone jar; the filter's remaining
 * life lives on the item ({@link IndustrialFilterItem}), so the block entity holds no scrubber state.
 */
public final class ExhaustScrubber {
    private ExhaustScrubber() {}

    /** True if {@code stack} is a clean industrial filter the scrubber will burn. */
    public static boolean isFilter(ItemStack stack) {
        return stack.is(ModItems.INDUSTRIAL_FILTER.get());
    }

    /**
     * One server tick of exhaust handling for a generator: while {@code running}, a clean
     * industrial filter in {@code items[filterSlot]} captures the exhaust (no smog, no
     * pollution), otherwise the generator vents raw via {@link GeneratorEmissions}; an
     * idle generator emits nothing.
     */
    public static void tickExhaust(
            ServerLevel level, BlockPos pos, ItemStackHandler items, int filterSlot, boolean running) {
        if (running && !advance(items, filterSlot)) {
            GeneratorEmissions.emit(level, pos); // raw exhaust: smog + pollution (DESIGN.md §7)
        } else {
            GeneratorEmissions.stop(level, pos); // idle or scrubbed: no smog, no pollution
        }
    }

    /**
     * Advances the scrubber by one running tick. Returns {@code true} if this tick's exhaust was
     * captured (the generator ran clean), {@code false} if there is no usable filter so the caller
     * must vent. Spends one tick of clean life from {@code items[filterSlot]} and clogs the filter
     * to a dirty industrial filter when its life is exhausted.
     */
    public static boolean advance(ItemStackHandler items, int filterSlot) {
        ItemStack filter = items.getStackInSlot(filterSlot);
        if (!filter.is(ModItems.INDUSTRIAL_FILTER.get())) {
            return false; // empty, already dirty/wet, or a non-industrial item: vent raw
        }
        int remaining = IndustrialFilterItem.getRemaining(filter) - 1;
        if (remaining <= 0) {
            // Spent on this final captured tick — clog it; cleaning happens outside the generator.
            items.setStackInSlot(filterSlot, new ItemStack(ModItems.DIRTY_INDUSTRIAL_FILTER.get()));
            return true;
        }
        IndustrialFilterItem.setRemaining(filter, remaining);
        items.setStackInSlot(filterSlot, filter); // persist the component change + mark the BE dirty
        return true;
    }
}
