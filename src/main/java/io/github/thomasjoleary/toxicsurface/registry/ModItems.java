// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registry. Phase 1 establishes the {@link DeferredRegister} wiring with a
 * single placeholder item so the registry and creative tab are exercised end to
 * end; real items (Data Component filters/masks, hazmat armour, sludge bucket)
 * arrive in Phases 4–5 (DESIGN.md §3, §5).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ToxicSurface.MODID);

    /** Placeholder plain item; gains Data Component behaviour in Phase 4. */
    public static final DeferredItem<Item> CLEAN_AIR_FILTER = ITEMS.registerSimpleItem("clean_air_filter");

    private ModItems() {}
}
