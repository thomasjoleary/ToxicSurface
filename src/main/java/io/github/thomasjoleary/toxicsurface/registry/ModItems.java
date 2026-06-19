// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry. Holds the placeholder filter (Data Component behaviour arrives in
 * Phase 4) and the toxic sludge bucket (DESIGN.md §3 Toxic sludge).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ToxicSurface.MODID);

    /** Placeholder plain item; gains Data Component behaviour in Phase 4. */
    public static final DeferredItem<Item> CLEAN_AIR_FILTER = ITEMS.registerSimpleItem("clean_air_filter");

    /** Real fluid bucket for toxic sludge; empties back to an empty bucket. */
    public static final DeferredItem<BucketItem> SLUDGE_BUCKET = ITEMS.register(
            "sludge_bucket",
            () -> new BucketItem(
                    ModFluids.SLUDGE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private ModItems() {}
}
