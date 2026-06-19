// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.item.FaceMaskItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry: air filters, the face mask (DESIGN.md §3 Filters &amp; masks) and the
 * toxic sludge bucket (DESIGN.md §3 Toxic sludge).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ToxicSurface.MODID);

    /** Clean filter; crafted from 2 wool, installed into a face mask. */
    public static final DeferredItem<Item> CLEAN_AIR_FILTER = ITEMS.registerSimpleItem("clean_air_filter");

    /** Helmet-slot mask whose installed filter time is its durability bar. */
    public static final DeferredItem<FaceMaskItem> FACE_MASK =
            ITEMS.register("face_mask", () -> new FaceMaskItem(new Item.Properties().stacksTo(1)));

    /** Real fluid bucket for toxic sludge; empties back to an empty bucket. */
    public static final DeferredItem<BucketItem> SLUDGE_BUCKET = ITEMS.register(
            "sludge_bucket",
            () -> new BucketItem(
                    ModFluids.SLUDGE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private ModItems() {}
}
