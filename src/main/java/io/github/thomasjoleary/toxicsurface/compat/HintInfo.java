// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/**
 * Shared source of the recipe-viewer info entries (DESIGN.md §5 Phase 8), consumed by both the JEI
 * ({@code compat.jei}) and EMI ({@code compat.emi}) plugins so the two stay in lock-step. Each entry
 * pairs an item with a lang key for a description of a mechanic that has <b>no recipe view</b> — the
 * generator fuels and the industrial-filter clog/clean cycle.
 *
 * <p>Free of any JEI/EMI types so it lives in the base mod; the Create-gated generator block items
 * are resolved by registry id (skipped when absent) rather than referenced, so this never classloads
 * {@code compat.create}.
 */
public final class HintInfo {
    private HintInfo() {}

    /** One info page: the {@code item} it attaches to and the {@code key} of its description text. */
    public record Entry(ItemLike item, String key) {}

    public static List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        add(list, ModItems.CLEANSER.get(), "cleanser");
        add(list, ModItems.INDUSTRIAL_FILTER.get(), "industrial_filter");
        add(list, ModItems.DIRTY_INDUSTRIAL_FILTER.get(), "dirty_industrial_filter");
        add(list, ModItems.WET_INDUSTRIAL_FILTER.get(), "wet_industrial_filter");
        add(list, ModItems.TOXIC_RESIDUE.get(), "toxic_residue");
        add(list, ModItems.TOXIC_WASTE_BLOCK.get(), "toxic_waste_block");
        addById(list, "waste_generator");
        addById(list, "sludge_generator");
        return list;
    }

    /** The description text for an entry's {@code key}. */
    public static Component text(String key) {
        return Component.translatable("jei." + ToxicSurface.MODID + "." + key + ".info");
    }

    private static void add(List<Entry> list, ItemLike item, String key) {
        list.add(new Entry(item, key));
    }

    private static void addById(List<Entry> list, String path) {
        BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, path))
                .ifPresent(item -> list.add(new Entry(item, path)));
    }
}
