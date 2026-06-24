// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.item.FaceMaskItem;
import io.github.thomasjoleary.toxicsurface.item.HazmatChestItem;
import io.github.thomasjoleary.toxicsurface.item.IndustrialFilterItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry: air filters, the face mask, the hazmat suit (DESIGN.md §3) and the
 * toxic sludge bucket.
 */
public final class ModItems {
    /** Durability factor for hazmat armour (iron is 15, diamond 33). */
    private static final int HAZMAT_DURABILITY = 25;

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ToxicSurface.MODID);

    /** Clean filter; crafted from 2 wool, installed into a face mask. */
    public static final DeferredItem<Item> CLEAN_AIR_FILTER = ITEMS.registerSimpleItem("clean_air_filter");

    /** Spent filter ejected when refilling a mask; wash it back to clean (DESIGN.md §3). */
    public static final DeferredItem<Item> USED_AIR_FILTER = ITEMS.registerSimpleItem("used_air_filter");

    /**
     * Activated-carbon filter (DESIGN.md §3): a clean filter treated with charcoal in the
     * Weaver. Lasts {@code carbonFilterDurationMultiplier}× a plain filter; degrades to a
     * plain used filter when spent (re-treat after washing).
     */
    public static final DeferredItem<Item> CARBON_AIR_FILTER = ITEMS.registerSimpleItem("carbon_air_filter");

    /**
     * Industrial filter (DESIGN.md §7) — a reusable, generator-only scrubber filter (4 iron + 3
     * clean filters). It runs a toxic generator clean until it clogs, then cycles
     * {@link #DIRTY_INDUSTRIAL_FILTER dirty} → {@link #WET_INDUSTRIAL_FILTER wet} → clean. Never
     * usable in a face mask or hazmat suit.
     */
    public static final DeferredItem<IndustrialFilterItem> INDUSTRIAL_FILTER =
            ITEMS.register("industrial_filter", () -> new IndustrialFilterItem(new Item.Properties().stacksTo(1)));

    /** A clogged industrial filter; fan-wash it (Create splashing) to a {@link #WET_INDUSTRIAL_FILTER}. */
    public static final DeferredItem<Item> DIRTY_INDUSTRIAL_FILTER =
            ITEMS.registerSimpleItem("dirty_industrial_filter");

    /** A washed industrial filter; dry it with heat (furnace / fan + lava) back to a clean filter. */
    public static final DeferredItem<Item> WET_INDUSTRIAL_FILTER = ITEMS.registerSimpleItem("wet_industrial_filter");

    /** Helmet-slot mask whose installed filter time is its durability bar. */
    public static final DeferredItem<FaceMaskItem> FACE_MASK =
            ITEMS.register("face_mask", () -> new FaceMaskItem(new Item.Properties().stacksTo(1)));

    /** Real fluid bucket for toxic sludge; empties back to an empty bucket. */
    public static final DeferredItem<BucketItem> SLUDGE_BUCKET = ITEMS.register(
            "sludge_bucket",
            () -> new BucketItem(
                    ModFluids.SLUDGE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    /** Woven hazmat material (Weaver output in Phase 6); crafts the hazmat suit. */
    public static final DeferredItem<Item> HAZMAT_MATERIAL = ITEMS.registerSimpleItem("hazmat_material");

    /**
     * Toxic residue (DESIGN.md §3, §7) — the solid contaminant captured by Create reclamation:
     * dropped when fan-washing a used filter and when boiling sludge down. Reconstitutes into
     * sludge (mixing with water) or compacts into a {@link ModBlocks#TOXIC_WASTE_BLOCK}.
     */
    public static final DeferredItem<Item> TOXIC_RESIDUE = ITEMS.registerSimpleItem("toxic_residue");

    public static final DeferredItem<ArmorItem> HAZMAT_HELMET =
            ITEMS.register("hazmat_helmet", () -> armor(ArmorItem.Type.HELMET));
    public static final DeferredItem<HazmatChestItem> HAZMAT_CHESTPLATE = ITEMS.register(
            "hazmat_chestplate",
            () -> new HazmatChestItem(
                    ModArmorMaterials.HAZMAT,
                    new Item.Properties()
                            .durability(ArmorItem.Type.CHESTPLATE.getDurability(HAZMAT_DURABILITY))
                            .stacksTo(1)));
    public static final DeferredItem<ArmorItem> HAZMAT_LEGGINGS =
            ITEMS.register("hazmat_leggings", () -> armor(ArmorItem.Type.LEGGINGS));
    public static final DeferredItem<ArmorItem> HAZMAT_BOOTS =
            ITEMS.register("hazmat_boots", () -> armor(ArmorItem.Type.BOOTS));

    /** Block item for the Weaver machine (DESIGN.md §3). */
    public static final DeferredItem<BlockItem> WEAVER =
            ITEMS.register("weaver", () -> new BlockItem(ModBlocks.WEAVER.get(), new Item.Properties()));

    /** Block item for the Cleanser machine (DESIGN.md §3). */
    public static final DeferredItem<BlockItem> CLEANSER =
            ITEMS.register("cleanser", () -> new BlockItem(ModBlocks.CLEANSER.get(), new Item.Properties()));

    /** Block item for the compacted toxic waste block (DESIGN.md §7). */
    public static final DeferredItem<BlockItem> TOXIC_WASTE_BLOCK = ITEMS.register(
            "toxic_waste_block", () -> new BlockItem(ModBlocks.TOXIC_WASTE_BLOCK.get(), new Item.Properties()));

    private static ArmorItem armor(ArmorItem.Type type) {
        return new ArmorItem(
                ModArmorMaterials.HAZMAT,
                type,
                new Item.Properties().durability(type.getDurability(HAZMAT_DURABILITY)));
    }

    private ModItems() {}
}
