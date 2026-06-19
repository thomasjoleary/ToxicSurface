// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Mod-defined tags (DESIGN.md §3). */
public final class ModTags {
    private ModTags() {}

    public static final class Items {
        /** Items destroyed when they fall into toxic sludge (datapack-extensible). */
        public static final TagKey<Item> ORGANIC = tag("organic");

        private Items() {}

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, name));
        }
    }
}
